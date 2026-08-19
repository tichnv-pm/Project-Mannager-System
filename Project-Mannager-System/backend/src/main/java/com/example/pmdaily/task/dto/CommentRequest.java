package com.example.pmdaily.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request thêm/sửa bình luận (docs/api/05-task-api.md muc 3.12, BR-TASK-16).
 */
public record CommentRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống")
        @Size(min = 1, max = 2000, message = "Bình luận phải dài 1–2000 ký tự")
        String content) {
}
