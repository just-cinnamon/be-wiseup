package com.wiseup.api.presentation.resource;

import com.wiseup.api.application.dto.*;
import com.wiseup.api.domain.service.ModerationService;
import com.wiseup.api.presentation.filter.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

import java.util.List;

/**
 * Report and moderation endpoints – mount point: /api/reports
 *
 * <p>User routes require session + age + terms.
 * Admin routes additionally require is_admin.
 */
@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequireAuth
@RequireAdult
@RequireTerms
public class ReportsResource {

    @Inject
    ModerationService moderationService;

    /**
     * GET /api/reports/reasons
     * Returns the closed list of valid report reasons. Cached privately 1 h.
     */
    @GET
    @Path("/reasons")
    public Response getReasons() {
        return Response.ok(new ReasonsResponse(ModerationService.REASONS))
                .cacheControl(privateFor(3600))
                .build();
    }

    /**
     * POST /api/reports
     * Submits a content report. Certain reasons (minor, non-consent) trigger an
     * immediate block before any human review.
     */
    @POST
    public Response createReport(ReportRequest req, @Context ContainerRequestContext ctx) {
        SessionUser user = sessionUser(ctx);
        ReportResponse result = moderationService.report(user.id(), req);
        return Response.status(201)
                .entity(result)
                .build();
    }

    // ---- Admin-only routes ----

    /**
     * GET /api/reports/open
     * Open report queue (oldest first). Admin only.
     */
    @GET
    @Path("/open")
    @RequireAdmin
    public Response listOpen(@Context ContainerRequestContext ctx) {
        return Response.ok(new ReportsWrapper(moderationService.listOpen())).build();
    }

    /**
     * GET /api/reports/blocked
     * List of currently blocked videos with the blocking reason. Admin only.
     */
    @GET
    @Path("/blocked")
    @RequireAdmin
    public Response listBlocked(@Context ContainerRequestContext ctx) {
        return Response.ok(new BlockedWrapper(moderationService.listBlocked())).build();
    }

    /**
     * POST /api/reports/:id/review
     * Admin decision on a report: { "procede": true } blocks the video,
     * { "procede": false } may unblock it if this was the only block source.
     */
    @POST
    @Path("/{id}/review")
    @RequireAdmin
    public Response reviewReport(
            @PathParam("id") String id,
            ReviewRequest req,
            @Context ContainerRequestContext ctx
    ) {
        SessionUser admin = sessionUser(ctx);
        return Response.ok(moderationService.review(id, req.procede(), admin.id())).build();
    }

    /**
     * POST /api/reports/block
     * Manual block without a report. Admin only.
     */
    @POST
    @Path("/block")
    @RequireAdmin
    public Response blockVideo(BlockRequest req, @Context ContainerRequestContext ctx) {
        SessionUser admin = sessionUser(ctx);
        return Response.status(201)
                .entity(moderationService.block(req.videoId(), admin.id()))
                .build();
    }

    // -------------------------------------------------------------------------

    private SessionUser sessionUser(ContainerRequestContext ctx) {
        return (SessionUser) ctx.getProperty(AuthFilter.SESSION_USER_KEY);
    }

    private CacheControl privateFor(int seconds) {
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.setMaxAge(seconds);
        return cc;
    }

    record ReasonsResponse(List<String> reasons) {}
    record ReportsWrapper(Object reports) {}
    record BlockedWrapper(Object blocked) {}
}
