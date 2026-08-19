package com.example.pmdaily.plan;

/**
 * Loại tài nguyên gán vào planning task (docs/planning/10 muc 2) — PLN-RULE-RES-05.
 * TEAM loại khỏi v1; enum DB chỉ cho phép USER / ROLE / EXTERNAL.
 */
public enum ResourceType {
    USER,
    ROLE,
    EXTERNAL
}