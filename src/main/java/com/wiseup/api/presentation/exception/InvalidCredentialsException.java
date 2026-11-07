package com.wiseup.api.presentation.exception;

/** HTTP 401 – wrong email or password. Generic message prevents user enumeration. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("E-mail ou senha incorretos."); }
}
