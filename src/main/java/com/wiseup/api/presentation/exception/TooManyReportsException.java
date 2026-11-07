package com.wiseup.api.presentation.exception;

/** HTTP 429 – per-hour report rate limit exceeded. */
public class TooManyReportsException extends RuntimeException {
    public TooManyReportsException() {
        super("Você fez muitas denúncias na última hora. Tente novamente mais tarde.");
    }
}
