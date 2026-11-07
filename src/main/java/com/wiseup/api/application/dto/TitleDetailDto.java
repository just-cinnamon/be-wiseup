package com.wiseup.api.application.dto;

import java.util.List;

/** Full video detail returned by GET /api/videos/:id */
public record TitleDetailDto(
        String id,
        String title,
        String type,
        String backdrop,
        String poster,
        Integer year,
        String maturity,
        String duration,
        List<String> genres,
        int match,
        String description,
        boolean isNew,
        List<String> tags,
        long views,
        String publishedAt,
        String embedUrl
) {}
