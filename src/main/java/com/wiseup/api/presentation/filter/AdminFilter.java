package com.wiseup.api.presentation.filter;

import com.wiseup.api.application.dto.SessionUser;
import com.wiseup.api.presentation.exception.AdminOnlyException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/** Rejects requests from non-admin users. */
@Provider
@RequireAdmin
@Priority(Priorities.AUTHORIZATION)
public class AdminFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        SessionUser user = (SessionUser) ctx.getProperty(AuthFilter.SESSION_USER_KEY);
        if (user == null || !user.isAdmin()) {
            throw new AdminOnlyException();
        }
    }
}
