package com.example.pmdaily.sprint.dto;

import java.time.LocalDate;

/**
 * 1 điểm dữ liệu Burndown chart: remaining point thực tế (ghi nhận bởi snapshot hàng ngày)
 * so với đường remaining lý tưởng (tuyến tính theo thời gian sprint).
 */
public record BurndownPointResponse(
        LocalDate date,
        int totalStoryPoints,
        int completedStoryPoints,
        int actualRemainingStoryPoints,
        double idealRemainingStoryPoints) {
}
