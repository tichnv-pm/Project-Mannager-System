package com.example.pmdaily.plan.dto;

/**
 * Dòng diff 1 field của task giữa 2 version (docs/planning/11 muc 1, PLN-AC-VERSION-03).
 */
public record TaskDiffResponse(
        String wbsCode,
        String taskName,
        String field,
        Object fromValue,
        Object toValue) {
}