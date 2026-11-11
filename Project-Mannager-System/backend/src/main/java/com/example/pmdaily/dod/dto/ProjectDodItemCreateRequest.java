package com.example.pmdaily.dod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectDodItemCreateRequest(
        @NotBlank(message = "Nội dung tiêu chí không được để trống")
        @Size(max = 500, message = "Nội dung tối đa 500 ký tự")
        String text) {
}
