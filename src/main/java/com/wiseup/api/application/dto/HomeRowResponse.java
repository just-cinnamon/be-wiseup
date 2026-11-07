package com.wiseup.api.application.dto;

import java.util.List;

public record HomeRowResponse(String id, String title, List<TitleDto> items) {}
