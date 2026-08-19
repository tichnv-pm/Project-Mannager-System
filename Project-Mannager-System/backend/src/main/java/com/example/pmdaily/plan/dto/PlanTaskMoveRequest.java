package com.example.pmdaily.plan.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Di chuyển task trong WBS (docs/planning/07 muc 3) — PLN-FR-WBS-02.
 * UP/DOWN: đổi vị trí sibling; INDENT: thành con của sibling trước; OUTDENT: lên cấp ông nội;
 * TO_PARENT: gán parent mới (targetParentId null = root).
 */
public record PlanTaskMoveRequest(

        @NotNull(message = "direction is required")
        MoveDirection direction,

        UUID targetParentId
) {

    public enum MoveDirection {
        UP,
        DOWN,
        INDENT,
        OUTDENT,
        TO_PARENT
    }
}
