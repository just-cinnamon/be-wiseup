package com.wiseup.api.presentation.exception;

/** HTTP 400 – invalid query parameters or request body. */
public class InvalidParamsException extends RuntimeException {
    public InvalidParamsException(String message) { super(message); }
}
