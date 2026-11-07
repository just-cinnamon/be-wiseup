package com.wiseup.api.application.dto;

public record LoginRequest(String email, String password, String captchaToken) {}
