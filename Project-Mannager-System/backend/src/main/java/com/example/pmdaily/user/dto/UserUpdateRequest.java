package com.example.pmdaily.user.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sửa tài khoản (docs/api/02-user-admin-api.md muc 3.4) — roleIds thay thế toàn bộ vai trò hiện tại.
 */
public record UserUpdateRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        List<UUID> roleIds,

        @NotNull(message = "Version không được để trống")
        Long version) {
}
