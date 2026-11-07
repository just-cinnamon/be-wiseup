package com.wiseup.api.presentation.filter;

import com.wiseup.api.application.dto.SessionUser;
import com.wiseup.api.domain.service.AuthService;
import com.wiseup.api.presentation.exception.UnauthorizedException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * JAX-RS request filter that validates Bearer tokens and populates the
 * request context with the authenticated {@link SessionUser}.
 */
@Provider
@RequireAuth
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    public static final String SESSION_USER_KEY = "sessionUser";
    public static final String RAW_TOKEN_KEY    = "rawToken";

    @Inject
    AuthService authService;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String authorization = ctx.getHeaderString("Authorization");
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            throw new UnauthorizedException("Faça login para continuar.");
        }

        String rawToken = authorization.substring(7).trim();
        SessionUser user = authService.getSessionUser(rawToken);

        ctx.setProperty(SESSION_USER_KEY, user);
        ctx.setProperty(RAW_TOKEN_KEY,    rawToken);
    }
}
