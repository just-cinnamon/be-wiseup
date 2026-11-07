package com.wiseup.api.application.dto;

import java.util.Map;

public record MeResponse(SessionUser user, Map<String, String> legalVersions) {}
