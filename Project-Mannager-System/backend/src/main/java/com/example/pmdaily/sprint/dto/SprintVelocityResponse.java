package com.example.pmdaily.sprint.dto;

import java.util.UUID;

/**
 * Velocity của 1 sprint: tổng/đã hoàn thành story point (tasks status=DONE) trong sprint.
 */
public record SprintVelocityResponse(
        UUID sprintId,
        String sprintName,
        int totalStoryPoints,
        int completedStoryPoints,
        int totalTasks,
        int completedTasks) {
}
