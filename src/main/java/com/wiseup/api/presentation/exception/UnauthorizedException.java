package com.wiseup.api.presentation.exception;

/** HTTP 401 – missing or invalid session token. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() { super("Sessão expirada. Entre novamente."); }
    public UnauthorizedException(String message) { super(message); }
}
