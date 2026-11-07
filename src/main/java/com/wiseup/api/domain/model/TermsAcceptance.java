package com.wiseup.api.domain.model;

import java.time.OffsetDateTime;

/** One row in the terms_acceptances table. */
public record TermsAcceptance(
        String document,
        String version,
        OffsetDateTime acceptedAt
) {}
