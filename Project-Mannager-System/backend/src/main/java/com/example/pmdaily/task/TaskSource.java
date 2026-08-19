package com.example.pmdaily.task;

/**
 * Nguồn công việc (bảng tasks.source, mặc định MANUAL).
 * ACTION_ITEM khi task tạo từ action item (BR-AI-03).
 */
public enum TaskSource {
    MANUAL,
    MEETING,
    ACTION_ITEM,
    ISSUE,
    OTHER
}
