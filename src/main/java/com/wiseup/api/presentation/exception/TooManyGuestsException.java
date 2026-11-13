package com.wiseup.api.presentation.exception;

/** HTTP 429 – too many guest sessions opened from the same IP within one hour. */
public class TooManyGuestsException extends RuntimeException {
    public TooManyGuestsException() {
        super("Muitas entradas deste endereço na última hora. Tente novamente mais tarde.");
    }
}
