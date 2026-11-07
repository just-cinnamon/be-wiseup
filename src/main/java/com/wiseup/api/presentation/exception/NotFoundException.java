package com.wiseup.api.presentation.exception;

/** HTTP 404 – resource not found (video blocked or genuinely absent). */
public class NotFoundException extends RuntimeException {
    public NotFoundException() { super("Vídeo não encontrado."); }
    public NotFoundException(String message) { super(message); }
}
