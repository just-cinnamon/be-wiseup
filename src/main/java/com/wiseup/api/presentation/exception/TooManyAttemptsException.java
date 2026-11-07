package com.wiseup.api.presentation.exception;

/** HTTP 429 – too many failed login attempts; account temporarily locked. */
public class TooManyAttemptsException extends RuntimeException {
    private final int lockMinutes;

    public TooManyAttemptsException(int lockMinutes) {
        super("Muitas tentativas de login. Tente novamente em " + lockMinutes + " minutos.");
        this.lockMinutes = lockMinutes;
    }

    public int getLockMinutes() { return lockMinutes; }
}
