package com.wiseup.api.presentation.exception;

/** HTTP 403 – user has not accepted the current-version legal documents. */
public class TermsRequiredException extends RuntimeException {
    public TermsRequiredException() {
        super("Aceite os Termos de Uso e a Política de Privacidade para continuar.");
    }
}
