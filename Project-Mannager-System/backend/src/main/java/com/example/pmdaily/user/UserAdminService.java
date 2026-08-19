package com.example.pmdaily.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.auth.RefreshTokenRepository;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.dto.RolePermissionsRequest;
import com.example.pmdaily.user.dto.RoleResponse;
import com.example.pmdaily.user.dto.UserCreateRequest;
import com.example.pmdaily.user.dto.UserResponse;
import com.example.pmdaily.user.dto.UserStatusRequest;
import com.example.pmdaily.user.dto.UserUpdateRequest;
import com.example.pmdaily.user.mapper.UserMapper;

/**
 * Quản trị tài khoản, vai trò & quyền (docs/api/02-user-admin-api.md, FR-USER-01/02).
 * Quyền toàn cục qua @PreAuthorize ở controller (user:view / user:manage / role:manage).
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final String ENTITY_TYPE = "USER";
    private static final String ROLE_ENTITY_TYPE = "ROLE";
    private static final String DEFAULT_ROLE_CODE = "PROJECT_MEMBER";
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final List<String> SORT_WHITELIST =
            List.of("username", "fullName", "email", "status", "createdAt");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String keyword, UserStatus status, String roleCode,
            int page, int size, String sort) {
        validatePagination(page, size);
        Specification<User> spec = Specification.where(UserSpecification.notDeleted())
                .and(UserSpecification.keyword(keyword))
                .and(UserSpecification.status(status))
                .and(UserSpecification.roleCode(roleCode));
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<User> result = userRepository.findAll(spec, pageable);
        return PageResponse.of(result, userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(findUser(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Email đã tồn tại");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
        user.setRoles(resolveRoles(request.roleIds()));
        userRepository.save(user);

        auditService.record("USER_CREATED", ENTITY_TYPE, user.getId(), null,
                Map.of("username", user.getUsername()));
        log.info("user-admin.create success username={}", user.getUsername());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = findUser(id);
        if (user.getVersion() != request.version()) {
            throw new ConflictException();
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Email đã tồn tại");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(request.roleIds()));
        }
        userRepository.save(user);

        auditService.record("USER_UPDATED", ENTITY_TYPE, user.getId(), null,
                Map.of("username", user.getUsername()));
        log.info("user-admin.update success username={}", user.getUsername());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeStatus(UserPrincipal actor, UUID id, UserStatusRequest request) {
        User user = findUser(id);
        if (user.getVersion() != request.version()) {
            throw new ConflictException();
        }
        // Quy tắc 5 docs/05: ADMIN không thể vô hiệu hóa chính mình.
        if (request.status() == UserStatus.INACTIVE && actor.getId().equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể vô hiệu hóa chính tài khoản của bạn");
        }

        UserStatus previous = user.getStatus();
        user.setStatus(request.status());
        userRepository.save(user);

        if (request.status() == UserStatus.INACTIVE) {
            refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        }

        auditService.record("USER_STATUS_CHANGED", ENTITY_TYPE, user.getId(),
                Map.of("status", previous.name()), Map.of("status", request.status().name()));
        log.info("user-admin.change-status success username={} status={}", user.getUsername(), request.status());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional
    public RoleResponse updateRolePermissions(UUID roleId, RolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("vai trò", roleId));
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.permissionIds()));
        if (permissions.size() != request.permissionIds().size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Có quyền không tồn tại");
        }
        // FR-USER-02: không được gỡ quyền cuối cùng của vai trò ADMIN (quyền role:manage).
        if (ADMIN_ROLE_CODE.equals(role.getCode())
                && permissions.stream().noneMatch(p -> "role:manage".equals(p.getCode()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Không thể gỡ quyền role:manage của vai trò ADMIN");
        }

        role.setPermissions(permissions);
        roleRepository.save(role);

        auditService.record("ROLE_PERMISSIONS_UPDATED", ROLE_ENTITY_TYPE, role.getId(), null,
                Map.of("code", role.getCode(), "permissionCount", permissions.size()));
        log.info("user-admin.update-role-permissions success code={} count={}", role.getCode(), permissions.size());
        return toRoleResponse(role);
    }

    private RoleResponse toRoleResponse(Role role) {
        List<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem(), permissionCodes);
    }

    private User findUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", id));
        if (user.getDeletedAt() != null) {
            throw new ResourceNotFoundException("tài khoản", id);
        }
        return user;
    }

    /**
     * roleIds null → mặc định PROJECT_MEMBER (spec 3.3); ngược lại thay thế toàn bộ.
     */
    private Set<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Vai trò mặc định " + DEFAULT_ROLE_CODE + " chưa được khởi tạo"));
            return Set.of(defaultRole);
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Có vai trò không tồn tại");
        }
        return roles;
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "page phải >= 0 và size phải trong khoảng 1-100");
        }
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORT_WHITELIST.contains(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường sắp xếp không hợp lệ: " + field);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        User user = findUser(id);
        if (actor.getId().equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể tự xóa tài khoản của chính mình");
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể xóa tài khoản admin hệ thống mặc định");
        }
        
        user.setDeletedAt(Instant.now());
        user.setDeletedBy(actor.getId());
        
        String suffix = "_deleted_" + Instant.now().toEpochMilli();
        user.setUsername(user.getUsername() + suffix);
        user.setEmail(user.getEmail() + suffix);
        
        userRepository.save(user);
        
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        
        auditService.record("USER_DELETED", ENTITY_TYPE, user.getId(), null,
                Map.of("username", user.getUsername()));
        log.info("user-admin.delete success username={}", user.getUsername());
    }

    @Transactional
    public RoleResponse createRole(com.example.pmdaily.user.dto.RoleCreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Mã vai trò đã tồn tại");
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(request.description() != null ? request.description().trim() : null);
        role.setSystem(false);
        
        if (request.permissionCodes() != null && !request.permissionCodes().isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllByCodeIn(request.permissionCodes()));
            role.setPermissions(permissions);
        }
        
        roleRepository.save(role);
        
        auditService.record("ROLE_CREATED", ROLE_ENTITY_TYPE, role.getId(), null,
                Map.of("code", role.getCode()));
        log.info("user-admin.createRole success code={}", role.getCode());
        return toRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(UUID roleId, com.example.pmdaily.user.dto.RoleUpdateRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("vai trò", roleId));
        
        role.setName(request.name().trim());
        role.setDescription(request.description() != null ? request.description().trim() : null);
        
        if (request.permissionCodes() != null) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllByCodeIn(request.permissionCodes()));
            
            // FR-USER-02: không được gỡ quyền role:manage của vai trò ADMIN.
            if (role.isSystem() && "ADMIN".equals(role.getCode())
                    && permissions.stream().noneMatch(p -> "role:manage".equals(p.getCode()))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Không thể gỡ quyền role:manage của vai trò ADMIN");
            }
            role.setPermissions(permissions);
        }
        
        roleRepository.save(role);
        
        auditService.record("ROLE_UPDATED", ROLE_ENTITY_TYPE, role.getId(), null,
                Map.of("code", role.getCode()));
        log.info("user-admin.updateRole success code={}", role.getCode());
        return toRoleResponse(role);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("vai trò", roleId));
        if (role.isSystem()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể xóa vai trò mặc định của hệ thống");
        }
        
        roleRepository.delete(role);
        
        auditService.record("ROLE_DELETED", ROLE_ENTITY_TYPE, roleId, null,
                Map.of("code", role.getCode()));
        log.info("user-admin.deleteRole success code={}", role.getCode());
    }
}
