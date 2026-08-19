package com.example.pmdaily.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WikiPageUpdateRequest(
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
        String title,

        @NotBlank(message = "Nội dung không được để trống")
        String content,

        @NotNull(message = "Phiên bản không hợp lệ")
        Long version
) {}
