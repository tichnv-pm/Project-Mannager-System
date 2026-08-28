package com.example.pmdaily.project;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Vai trò trong dự án (docs/02-functional-requirements.md muc 1.6, BR-PROJ-10).
 */
public enum ProjectMemberRole {
    PROJECT_MANAGER, TECH_LEAD, BUSINESS_ANALYST, DEVELOPER, TESTER, DEVOPS, MEMBER;

    @JsonCreator
    public static ProjectMemberRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "DEV", "DEVELOPER" -> DEVELOPER;
            case "BA", "BUSINESS_ANALYST" -> BUSINESS_ANALYST;
            case "TECH_LEAD", "LEAD", "TECHLEAD" -> TECH_LEAD;
            case "TESTER", "QA", "QC" -> TESTER;
            case "DEVOPS", "OPS" -> DEVOPS;
            case "PROJECT_MANAGER", "PM" -> PROJECT_MANAGER;
            case "MEMBER", "PROJECT_MEMBER" -> MEMBER;
            default -> {
                for (ProjectMemberRole role : values()) {
                    if (role.name().equalsIgnoreCase(normalized)) {
                        yield role;
                    }
                }
                throw new IllegalArgumentException("Vai trò dự án không hợp lệ: " + value);
            }
        };
    }
}

