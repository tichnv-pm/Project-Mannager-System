package com.example.pmdaily.common;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Tiện ích thời gian: DB lưu UTC, UI hiển thị theo giờ người dùng (docs/00-project-overview.md muc 10).
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static Instant nowUtc() {
        return Instant.now();
    }

    public static ZoneOffset utcOffset() {
        return ZoneOffset.UTC;
    }
}
