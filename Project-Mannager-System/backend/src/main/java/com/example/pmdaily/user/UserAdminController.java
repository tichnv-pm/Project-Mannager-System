package com.example.pmdaily.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.dto.RoleCreateRequest;
import com.example.pmdaily.user.dto.RolePermissionsRequest;
import com.example.pmdaily.user.dto.RoleResponse;
import com.example.pmdaily.user.dto.RoleUpdateRequest;
import com.example.pmdaily.user.dto.UserCreateRequest;
import com.example.pmdaily.user.dto.UserResponse;
import com.example.pmdaily.user.dto.UserStatusRequest;
import com.example.pmdaily.user.dto.UserUpdateRequest;

/**
 * Quản trị người dùng, vai trò & quyền (docs/api/02-user-admin-api.md).
 */
@RestController
@RequestMapping("/api/v1")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:view')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String roleCode) {
        return userAdminService.list(keyword, status, roleCode, page, size, sort);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:manage')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userAdminService.create(request);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public UserResponse get(@PathVariable UUID id) {
        return userAdminService.get(id);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return userAdminService.update(id, request);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserResponse changeStatus(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request) {
        return userAdminService.changeStatus(actor, id, request);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        userAdminService.delete(actor, id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public List<RoleResponse> listRoles() {
        return userAdminService.listRoles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role:manage')")
    public RoleResponse createRole(@Valid @RequestBody RoleCreateRequest request) {
        return userAdminService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:manage')")
    public RoleResponse updateRole(@PathVariable UUID roleId, @Valid @RequestBody RoleUpdateRequest request) {
        return userAdminService.updateRole(roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:manage')")
    public void deleteRole(@PathVariable UUID roleId) {
        userAdminService.deleteRole(roleId);
    }
}
