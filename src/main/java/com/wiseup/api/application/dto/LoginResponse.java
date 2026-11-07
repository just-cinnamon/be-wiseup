package com.wiseup.api.application.dto;

public record LoginResponse(SessionUser user, String token, String expiresAt) {}
