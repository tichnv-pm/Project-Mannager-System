package com.example.pmdaily.plan;

/**
 * Loại exception của working calendar (docs/database/02 muc 31) — PLN-FR-CAL-03/04.
 * NON_WORKING = ngày nghỉ / holiday; WORKING = ngày làm bù (biến ngày thường thành ngày làm việc).
 */
public enum CalendarExceptionType {

    NON_WORKING, WORKING
}