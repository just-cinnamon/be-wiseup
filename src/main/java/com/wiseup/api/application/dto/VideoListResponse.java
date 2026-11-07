package com.wiseup.api.application.dto;

import java.util.List;

public record VideoListResponse(int page, List<TitleDto> items, boolean hasMore) {}
