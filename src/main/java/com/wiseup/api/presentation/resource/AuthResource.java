package com.wiseup.api.presentation.resource;

import com.wiseup.api.application.dto.*;
import com.wiseup.api.domain.repository.UserRepository;
import com.wiseup.api.domain.service.AuthService;
import com.wiseup.api.infrastructure.security.GuestThrottle;
import com.wiseup.api.presentation.filter.AuthFilter;
import com.wiseup.api.presentation.filter.RequireAuth;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

/**
 * Authentication endpoints – mount point: /api/auth
 *
 * <p>Cache-Control: no-store is set on every response in this resource so that
 * shared caches (CDN, proxy) never store tokens or user data.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    UserRepository userRepository;

    @Inject
    GuestThrottle guestThrottle;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;

    // -------------------------------------------------------------------------

    /**
     * POST /api/auth/login
     * Open (no auth required). Returns token + user metadata on success.
     */
    @POST
    @Path("/login")
    public Response login(LoginRequest req, @Context ContainerRequestContext ctx) {
        String clientIp = resolveClientIp(headers);
        LoginResponse result = authService.login(req, clientIp);

        // Record access log (Marco Civil da Internet art.15) – best-effort
        try {
            userRepository.recordAccessLog(result.user().id(), clientIp);
        } catch (Exception ignored) { /* access-log failure must not block login */ }

        return Response.ok(result)
                .cacheControl(noStore())
                .build();
    }

    /**
     * POST /api/auth/guest
     * Open (no auth required). Opens a session without an account for whoever
     * has just declared being 18 or older in the +18 gate – which, since the
     * front-end dropped login (ADR-0023 of be-wiseup-one), is the only entrance
     * to the site.
     *
     * <p>Takes no body: there is nothing to send. The declaration itself is the
     * request, and the IP and timestamp come from the server.
     */
    @POST
    @Path("/guest")
    public Response guest() {
        String clientIp = resolveClientIp(headers);

        // Open endpoint that writes a row per call – see GuestThrottle.
        guestThrottle.check(clientIp);

        LoginResponse result = authService.guest(clientIp);

        // Record access log (Marco Civil da Internet art.15) – best-effort
        try {
            userRepository.recordAccessLog(result.user().id(), clientIp);
        } catch (Exception ignored) { /* access-log failure must not block entry */ }

        return Response.ok(result)
                .cacheControl(noStore())
                .build();
    }

    /**
     * GET /api/auth/me
     * Returns current session user + current legal versions.
     * Front-end calls this on startup to know which screen to show.
     */
    @GET
    @Path("/me")
    @RequireAuth
    public Response me(@Context ContainerRequestContext ctx) {
        SessionUser user = sessionUser(ctx);
        return Response.ok(authService.me(user))
                .cacheControl(noStore())
                .build();
    }

    /**
     * POST /api/auth/accept
     * Adult self-declaration + term acceptance in one atomic action.
     * Requires a valid session but NOT age/terms (would be a deadlock).
     */
    @POST
    @Path("/accept")
    @RequireAuth
    public Response accept(@Context ContainerRequestContext ctx) {
        SessionUser user = sessionUser(ctx);
        String ip = resolveClientIp(headers);
        return Response.ok(authService.acceptGate(user.id(), ip))
                .cacheControl(noStore())
                .build();
    }

    /**
     * POST /api/auth/logout
     * Deletes the session row; JWTs couldn't do this.
     */
    @POST
    @Path("/logout")
    @RequireAuth
    public Response logout(@Context ContainerRequestContext ctx) {
        String rawToken = (String) ctx.getProperty(AuthFilter.RAW_TOKEN_KEY);
        authService.logout(rawToken);
        return Response.ok(new OkResponse(true))
                .cacheControl(noStore())
                .build();
    }

    /**
     * DELETE /api/auth/me
     * Erases the account and all its sessions (LGPD right to erasure).
     */
    @DELETE
    @Path("/me")
    @RequireAuth
    public Response deleteAccount(@Context ContainerRequestContext ctx) {
        SessionUser user = sessionUser(ctx);
        authService.deleteAccount(user.id());
        return Response.ok(new OkResponse(true))
                .cacheControl(noStore())
                .build();
    }

    // -------------------------------------------------------------------------

    private SessionUser sessionUser(ContainerRequestContext ctx) {
        return (SessionUser) ctx.getProperty(AuthFilter.SESSION_USER_KEY);
    }

    private String resolveClientIp(HttpHeaders headers) {
        // Trust one proxy hop (set via quarkus.http.proxy config)
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private CacheControl noStore() {
        CacheControl cc = new CacheControl();
        cc.setNoStore(true);
        return cc;
    }

    record OkResponse(boolean ok) {}
}
