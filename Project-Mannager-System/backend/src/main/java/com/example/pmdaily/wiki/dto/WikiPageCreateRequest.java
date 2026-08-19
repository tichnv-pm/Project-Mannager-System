package com.example.pmdaily.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WikiPageCreateRequest(
        UUID parentPageId,

        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
        String title,

        @NotBlank(message = "Nội dung không được để trống")
        String content
) {}
