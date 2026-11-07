package com.wiseup.api.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Minimal video card shown in lists and home rows. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TitleDto(
        String id,
        String title,
        String type,
        String backdrop,
        String poster,
        Integer year,
        String maturity,
        String duration,
        java.util.List<String> genres,
        int match,
        String description,
        boolean isNew,
        Integer rank   // only on the "featured" slot
) {
    /** Used when rank is not applicable. */
    public TitleDto(String id, String title, String type, String backdrop, String poster,
                    Integer year, String maturity, String duration, java.util.List<String> genres,
                    int match, String description, boolean isNew) {
        this(id, title, type, backdrop, poster, year, maturity, duration, genres, match, description, isNew, null);
    }

    /** Copy constructor that sets the rank. */
    public TitleDto(TitleDto base, Integer rank) {
        this(base.id(), base.title(), base.type(), base.backdrop(), base.poster(),
             base.year(), base.maturity(), base.duration(), base.genres(),
             base.match(), base.description(), base.isNew(), rank);
    }
}
