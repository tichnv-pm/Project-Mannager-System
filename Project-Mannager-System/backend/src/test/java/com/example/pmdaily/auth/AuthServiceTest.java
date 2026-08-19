package com.example.pmdaily.auth;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import com.example.pmdaily.user.mapper.UserMapper;
import com.example.pmdaily.user.mapper.UserMapperImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private final UserMapper userMapper = new UserMapperImpl();
    private AuthService authService;

    private User activeUser;
    private final String rawPassword = "Abc@12345";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtService,
                passwordEncoder, userMapper, auditService, 900_000, 604_800_000L, 5, 5);
        activeUser = new User();
        activeUser.setId(UUID.randomUUID());
        activeUser.setUsername("pm.minh");
        activeUser.setEmail("minh@pmdaily.local");
        activeUser.setFullName("Nguyễn Văn Minh");
        activeUser.setPasswordHash("hashed-password");
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    private void stubSaveReturnsArgument() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
    }

    @Test
    void loginSuccess_returnsTokens_andResetsAttempts() {
        stubSaveReturnsArgument();
        when(userRepository.findByUsername("pm.minh")).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.matches(rawPassword, "hashed-password")).thenReturn(true);

        TokenResponse response = authService.login(new LoginRequest("pm.minh", rawPassword), "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().username()).isEqualTo("pm.minh");
        assertThat(response.user().email()).isEqualTo("minh@pmdaily.local");
        assertThat(activeUser.getFailedLoginAttempts()).isZero();
        assertThat(activeUser.getLastLoginAt()).isNotNull();
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("LOGIN_SUCCESS"), any(), any(), any(), any());
    }

    @Test
    void login_wrongPassword_throwsInvalidLogin_andIncrementsAttempts() {
        when(userRepository.findByUsername("pm.minh")).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.matches("Wrong@123", "hashed-password")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.login(new LoginRequest("pm.minh", "Wrong@123"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);
        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_fifthFailure_locksAccount() {
        when(userRepository.findByUsername("pm.minh")).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("pm.minh", "Wrong@123"), "ip"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_LOGIN);
        }
        assertThatThrownBy(() -> authService.login(new LoginRequest("pm.minh", "Wrong@123"), "ip"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(activeUser.getLockedUntil()).isNotNull();
        assertThat(activeUser.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void login_whenLocked_throwsAccountLocked() {
        activeUser.setLockedUntil(Instant.now().plusSeconds(300));
        when(userRepository.findByUsername("pm.minh")).thenReturn(java.util.Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("pm.minh", rawPassword), "ip"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void login_inactiveAccount_throwsInvalidLogin_sameMessage() {
        activeUser.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByUsername("pm.minh")).thenReturn(java.util.Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("pm.minh", rawPassword), "ip"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_userNotFound_throwsInvalidLogin_sameMessage() {
        when(userRepository.findByUsername("unknown")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", rawPassword), "ip"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);
    }

    @Test
    void refresh_success_rotatesToken() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUser(activeUser);
        oldToken.setTokenHash("old-hash");
        oldToken.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(java.util.Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");

        TokenResponse response = authService.refresh(new RefreshRequest("old-raw"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(oldToken.getReplacedBy()).isNotNull();
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("REFRESH_TOKEN"), any(), any(), any(), any());
    }

    @Test
    void refresh_revokedToken_detectsReuse_andRevokesAll() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUser(activeUser);
        oldToken.setTokenHash("old-hash");
        oldToken.setRevokedAt(Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(java.util.Optional.of(oldToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-raw")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(refreshTokenRepository).revokeAllByUserId(eq(activeUser.getId()), any(Instant.class));
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setUser(activeUser);
        oldToken.setTokenHash("old-hash");
        oldToken.setExpiresAt(Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(java.util.Optional.of(oldToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-raw")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("unknown")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void logout_revokesToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(activeUser);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(java.util.Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(new LogoutRequest("raw"));

        assertThat(token.getRevokedAt()).isNotNull();
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("LOGOUT"), any(), any(), any(), any());
    }

    @Test
    void logout_unknownToken_isIdempotent() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(java.util.Optional.empty());

        authService.logout(new LogoutRequest("unknown"));
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void changePassword_success_encodesNewPassword_andRevokesAll() {
        when(userRepository.findById(activeUser.getId())).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.matches("Old@12345", "hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("New@12345")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword(
                new UserPrincipal(activeUser.getId(), "pm.minh", java.util.List.of(), java.util.List.of()),
                new ChangePasswordRequest("Old@12345", "New@12345"));

        assertThat(activeUser.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository).revokeAllByUserId(eq(activeUser.getId()), any(Instant.class));
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsValidationError() {
        when(userRepository.findById(activeUser.getId())).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.matches("Wrong@123", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                new UserPrincipal(activeUser.getId(), "pm.minh", java.util.List.of(), java.util.List.of()),
                new ChangePasswordRequest("Wrong@123", "New@12345")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void resetPassword_success() {
        when(userRepository.findById(activeUser.getId())).thenReturn(java.util.Optional.of(activeUser));
        when(passwordEncoder.encode("New@12345")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword(activeUser.getId(), new ResetPasswordRequest("New@12345"));

        assertThat(activeUser.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository).revokeAllByUserId(eq(activeUser.getId()), any(Instant.class));
    }

    @Test
    void resetPassword_userNotFound_throws404() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(missingId, new ResetPasswordRequest("New@12345")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }
}
