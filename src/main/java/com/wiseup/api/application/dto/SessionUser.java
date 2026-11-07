package com.wiseup.api.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authenticated user context carried in every secured request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionUser(
        String id,
        String email,
        boolean ageVerified,
        boolean termsAccepted,
        boolean isAdmin
) {}
