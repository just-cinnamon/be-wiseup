package com.wiseup.api.application.dto;

import java.util.List;

public record HomeResponse(TitleDto featured, List<TitleDto> top10, List<HomeRowResponse> rows) {}
