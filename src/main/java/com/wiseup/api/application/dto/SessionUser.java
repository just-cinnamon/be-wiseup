package com.wiseup.api.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authenticated user context carried in every secured request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionUser(
        String id,
        String email,
        boolean ageVerified,
        boolean termsAccepted,
        boolean isAdmin,
        boolean isGuest
) {
    public SessionUser(String id, String email, boolean ageVerified, boolean termsAccepted, boolean isAdmin) {
        this(id, email, ageVerified, termsAccepted, isAdmin, false);
    }
}
