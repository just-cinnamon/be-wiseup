package com.wiseup.api.presentation.exception;

/** HTTP 403 – route restricted to admin users. */
public class AdminOnlyException extends RuntimeException {
    public AdminOnlyException() { super("Esta área é restrita à moderação."); }
}
