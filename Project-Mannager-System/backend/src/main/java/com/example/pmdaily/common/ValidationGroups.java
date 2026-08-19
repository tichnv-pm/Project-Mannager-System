package com.example.pmdaily.common;

import jakarta.validation.groups.Default;

/**
 * Validation groups: create/update (docs/design/02-backend-architecture.md muc 5).
 * VD: field {@code version} chỉ bắt buộc khi update.
 */
public interface ValidationGroups {

    interface Create extends Default {
    }

    interface Update extends Default {
    }
}
