package com.example.pmdaily.project.dto;

import java.util.UUID;

public record ProjectMemberFinanceResponse(
    UUID memberId,
    UUID userId,
    String username,
    String fullName,
    String role,
    Double hourlyRate
) {}
