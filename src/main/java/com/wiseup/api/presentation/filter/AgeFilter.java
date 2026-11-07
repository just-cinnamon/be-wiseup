package com.wiseup.api.presentation.filter;

import com.wiseup.api.application.dto.SessionUser;
import com.wiseup.api.presentation.exception.AgeRequiredException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Rejects requests from users who have not completed the adult self-declaration gate.
 * Must run after {@link AuthFilter} (higher priority number = later execution).
 */
@Provider
@RequireAdult
@Priority(Priorities.AUTHENTICATION + 1)
public class AgeFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        SessionUser user = (SessionUser) ctx.getProperty(AuthFilter.SESSION_USER_KEY);
        if (user == null || !user.ageVerified()) {
            throw new AgeRequiredException();
        }
    }
}
