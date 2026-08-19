package com.example.pmdaily.user.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
    @NotBlank(message = "Mã vai trò không được để trống")
    @Size(min = 2, max = 50, message = "Mã vai trò từ 2 đến 50 ký tự")
    String code,

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(min = 2, max = 100, message = "Tên vai trò từ 2 đến 100 ký tự")
    String name,

    @Size(max = 255, message = "Mô tả không quá 255 ký tự")
    String description,

    List<String> permissionCodes
) {}
