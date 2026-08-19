package com.example.pmdaily.common;

/**
 * Hằng số dùng chung.
 */
public final class Constants {

    public static final String API_PREFIX = "/api/v1";

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE = 0;

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";

    private Constants() {
    }
}
