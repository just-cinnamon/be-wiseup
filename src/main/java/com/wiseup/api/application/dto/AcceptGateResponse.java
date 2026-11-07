package com.wiseup.api.application.dto;

import java.util.Map;

public record AcceptGateResponse(
        boolean ageVerified,
        boolean termsAccepted,
        String acceptedAt,
        Map<String, String> versions
) {}
