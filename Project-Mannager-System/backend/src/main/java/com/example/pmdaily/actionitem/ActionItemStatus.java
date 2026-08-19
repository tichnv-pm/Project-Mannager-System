package com.example.pmdaily.actionitem;

/**
 * Trạng thái action item (docs/api/07-action-item-api.md, UC-007).
 * DONE bắt buộc progress = 100 (ck_action_items_done trong schema).
 */
public enum ActionItemStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
