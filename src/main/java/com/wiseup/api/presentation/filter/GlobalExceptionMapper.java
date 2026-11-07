package com.wiseup.api.presentation.filter;

import com.wiseup.api.infrastructure.external.MediaProviderClient;
import com.wiseup.api.presentation.exception.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Converts all application and JAX-RS exceptions into the standard error JSON format:
 * <pre>{ "error": { "code": "...", "message": "..." } }</pre>
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable ex) {
        return switch (ex) {
            case InvalidParamsException e    -> error(400, "INVALID_PARAMS",    e.getMessage());
            case CaptchaFailedException e    -> error(400, "CAPTCHA_FAILED",    e.getMessage());
            case InvalidCredentialsException e -> error(401, "INVALID_CREDENTIALS", e.getMessage());
            case UnauthorizedException e     -> error(401, "UNAUTHORIZED",      e.getMessage());
            case AgeRequiredException e      -> error(403, "AGE_REQUIRED",      e.getMessage());
            case TermsRequiredException e    -> error(403, "TERMS_REQUIRED",    e.getMessage());
            case AdminOnlyException e        -> error(403, "ADMIN_ONLY",        e.getMessage());
            case com.wiseup.api.presentation.exception.NotFoundException e -> error(404, "NOT_FOUND", e.getMessage());
            case jakarta.ws.rs.NotFoundException e -> error(404, "ROUTE_NOT_FOUND", "Rota não encontrada.");
            case TooManyAttemptsException e  -> error(429, "TOO_MANY_ATTEMPTS", e.getMessage());
            case TooManyReportsException e   -> error(429, "TOO_MANY_REPORTS",  e.getMessage());
            case MediaProviderClient.UpstreamException e -> error(502, "UPSTREAM_UNAVAILABLE",
                    "Não foi possível carregar os vídeos agora.");
            case WebApplicationException e   -> error(e.getResponse().getStatus(), "HTTP_ERROR", e.getMessage());
            default -> {
                // Log unexpected errors without leaking internal details
                org.jboss.logging.Logger.getLogger(GlobalExceptionMapper.class)
                        .error("Unexpected error", ex);
                yield error(500, "INTERNAL_ERROR", "Erro inesperado no servidor.");
            }
        };
    }

    private Response error(int status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", Map.of("code", code, "message", message)))
                .build();
    }
}
