package com.wiseup.api.presentation.exception;

/** HTTP 403 – user has not completed the age-verification gate. */
public class AgeRequiredException extends RuntimeException {
    public AgeRequiredException() {
        super("Confirme que você é maior de 18 anos para acessar o conteúdo.");
    }
}
