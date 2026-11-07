package com.wiseup.api.application.dto;

/** Query parameters for GET /api/videos */
public record VideoListQuery(String q, String category, String ordering, String period, Integer page) {}
