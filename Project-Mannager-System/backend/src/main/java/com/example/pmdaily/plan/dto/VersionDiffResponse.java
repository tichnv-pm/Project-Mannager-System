package com.example.pmdaily.plan.dto;

import java.util.List;

/**
 * Diff version: so sánh versionNo với version liền sau (vs+1).
 */
public record VersionDiffResponse(
        int versionNo,
        int compareToVersionNo,
        List<TaskDiffResponse> tasks) {
}