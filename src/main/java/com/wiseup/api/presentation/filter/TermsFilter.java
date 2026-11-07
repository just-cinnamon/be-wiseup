package com.wiseup.api.presentation.filter;

import com.wiseup.api.application.dto.SessionUser;
import com.wiseup.api.presentation.exception.TermsRequiredException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/** Rejects requests from users who have not accepted the current-version legal documents. */
@Provider
@RequireTerms
@Priority(Priorities.AUTHENTICATION + 2)
public class TermsFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        SessionUser user = (SessionUser) ctx.getProperty(AuthFilter.SESSION_USER_KEY);
        if (user == null || !user.termsAccepted()) {
            throw new TermsRequiredException();
        }
    }
}
