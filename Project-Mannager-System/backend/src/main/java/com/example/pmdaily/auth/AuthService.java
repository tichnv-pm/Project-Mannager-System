package com.example.pmdaily.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.auth.dto.ChangePasswordRequest;
import com.example.pmdaily.auth.dto.LoginRequest;
import com.example.pmdaily.auth.dto.LogoutRequest;
import com.example.pmdaily.auth.dto.RefreshRequest;
import com.example.pmdaily.auth.dto.ResetPasswordRequest;
import com.example.pmdaily.auth.dto.TokenResponse;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.security.JwtService;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.example.pmdaily.user.dto.UserResponse;
import com.example.pmdaily.user.mapper.UserMapper;

/**
 * Nghiệp vụ xác thực (docs/api/01-auth-api.md, docs/use-cases/UC-001-login.md).
 * - Login: BCrypt verify; khóa tạm 5 lần sai/5 phút (BR-AUTH-08); message chung (BR-AUTH-05/06).
 * - Refresh: rotation — token cũ revoke + replaced_by; phát hiện reuse → revoke toàn bộ (BR-AUTH-09).
 * - Logout idempotent; change/reset password → revoke toàn bộ refresh token (BR-AUTH-04).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTITY_TYPE = "USER";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;

    private final Duration accessExpiration;
    private final Duration refreshExpiration;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public AuthService(UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuditService auditService,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login.lock-duration-minutes:5}") long lockDurationMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.accessExpiration = Duration.ofMillis(accessExpirationMs);
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMs);
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    /**
     * noRollbackFor: khi login thất bại vẫn phải lưu attempts/lock và audit
     * (BR-AUTH-08) — exception nghiệp vụ không được rollback những ghi nhận này.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(LoginRequest request, String clientIp) {
        Instant now = Instant.now();
        User user = userRepository.findByUsername(request.username().trim()).orElse(null);
        if (user == null || user.getDeletedAt() != null) {
            auditFailedLogin(request.username(), clientIp);
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        if (user.isLocked(now)) {
            auditFailedLogin(user.getUsername(), clientIp);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditFailedLogin(user.getUsername(), clientIp);
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            auditFailedLogin(user.getUsername(), clientIp);
            if (user.isLocked(Instant.now())) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        TokenResponse response = issueTokenPair(user, now);
        auditService.record("LOGIN_SUCCESS", ENTITY_TYPE, user.getId(), null,
                java.util.Map.of("username", user.getUsername(), "ip", clientIp));
        log.info("auth.login success username={} ip={}", user.getUsername(), clientIp);
        return response;
    }

    /**
     * noRollbackFor: khi phát hiện reuse vẫn phải commit việc revoke toàn bộ chain
     * (BR-AUTH-09) trước khi ném UNAUTHORIZED.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();
        String tokenHash = sha256Hex(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (stored == null) {
            log.warn("auth.refresh token-not-found");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (stored.getRevokedAt() != null) {
            revokeChain(stored.getUser().getId(), now);
            log.warn("auth.refresh reuse-detected userId={}", stored.getUser().getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (stored.getExpiresAt().isBefore(now)) {
            log.warn("auth.refresh token-expired userId={}", stored.getUser().getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = stored.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            log.warn("auth.refresh user-not-active-or-deleted userId={}", user.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TokenPair replacement = issueRefreshToken(user, now);
        stored.setRevokedAt(now);
        stored.setReplacedBy(replacement.token().getId());
        refreshTokenRepository.save(stored);

        UserResponse userResponse = userMapper.toResponse(user);
        auditService.record("REFRESH_TOKEN", ENTITY_TYPE, user.getId(), null,
                java.util.Map.of("username", user.getUsername()));
        log.info("auth.refresh success username={}", user.getUsername());
        return buildTokenResponse(replacement.raw(), userResponse);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(request.refreshToken())).orElse(null);
        if (stored != null && stored.getRevokedAt() == null) {
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
            auditService.record("LOGOUT", ENTITY_TYPE, stored.getUser().getId(), null,
                    java.util.Map.of("username", stored.getUser().getUsername()));
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", principal.getId()));
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        Instant now = Instant.now();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", principal.getId()));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu hiện tại không đúng");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới phải khác mật khẩu cũ");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        auditService.record("PASSWORD_CHANGED", ENTITY_TYPE, user.getId(), null,
                java.util.Map.of("username", user.getUsername()));
        log.info("auth.change-password success username={}", user.getUsername());
    }

    @Transactional
    public void resetPassword(UUID targetUserId, ResetPasswordRequest request) {
        Instant now = Instant.now();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", targetUserId));
        target.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        userRepository.save(target);
        refreshTokenRepository.revokeAllByUserId(target.getId(), now);
        auditService.record("PASSWORD_RESET", ENTITY_TYPE, target.getId(), null,
                java.util.Map.of("username", target.getUsername()));
        log.info("auth.reset-password success userId={}", target.getId());
    }

    private void registerFailedAttempt(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
            user.setLockedUntil(Instant.now().plus(lockDuration));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
        log.warn("auth.login failed username={} attempts={} locked={}",
                user.getUsername(), maxFailedAttempts, user.getLockedUntil() != null);
    }

    private void auditFailedLogin(String username, String clientIp) {
        auditService.record("LOGIN_FAILED", ENTITY_TYPE, null, null,
                java.util.Map.of("username", username, "ip", clientIp));
    }

    private void revokeChain(UUID userId, Instant now) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId, now);
        auditService.record("REFRESH_TOKEN_REUSE", ENTITY_TYPE, userId, null,
                java.util.Map.of("revokedCount", revoked));
    }

    private TokenResponse issueTokenPair(User user, Instant now) {
        TokenPair pair = issueRefreshToken(user, now);
        return buildTokenResponse(pair.raw(), userMapper.toResponse(user));
    }

    private TokenPair issueRefreshToken(User user, Instant now) {
        String raw = UUID.randomUUID() + "-" + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash(sha256Hex(raw));
        token.setExpiresAt(now.plus(refreshExpiration));
        token.setCreatedBy(user.getId());
        refreshTokenRepository.save(token);
        return new TokenPair(raw, token);
    }

    private TokenResponse buildTokenResponse(String rawRefreshToken, UserResponse user) {
        UserPrincipal principal = new UserPrincipal(
                user.id(), user.username(), user.roles(), user.permissions());
        String accessToken = jwtService.generateAccessToken(principal);
        return TokenResponse.of(accessToken, rawRefreshToken, accessExpiration.toSeconds(), user);
    }

    private record TokenPair(String raw, RefreshToken token) {
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }
}
