package com.example.pmdaily.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Đổi mật khẩu (FR-AUTH-05, BR-AUTH-02).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "Mật khẩu phải từ 8 ký tự, gồm chữ thường, chữ hoa, số và ký tự đặc biệt")
        String newPassword) {
}
