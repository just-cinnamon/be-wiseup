package com.wiseup.api.presentation.exception;

/** HTTP 400 – reCAPTCHA token missing or rejected. */
public class CaptchaFailedException extends RuntimeException {
    public CaptchaFailedException() {
        super("Confirme que você não é um robô e tente de novo.");
    }
}
