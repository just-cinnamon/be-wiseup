package com.wiseup.api.presentation.resource;

import com.wiseup.api.application.dto.*;
import com.wiseup.api.domain.service.VideosService;
import com.wiseup.api.presentation.filter.RequireAdult;
import com.wiseup.api.presentation.filter.RequireAuth;
import com.wiseup.api.presentation.filter.RequireTerms;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

/**
 * Catalogue endpoints – mount point: /api
 *
 * <p>All routes require a valid session ({@link RequireAuth}),
 * age verification ({@link RequireAdult}) and accepted terms ({@link RequireTerms}).
 * Cache-Control is {@code private} (not public) because responses depend on
 * which user is calling (blocked-video filtering is per-account).
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RequireAuth
@RequireAdult
@RequireTerms
public class CatalogueResource {

    @Inject
    VideosService videosService;

    /**
     * GET /api/home
     * Returns the full home-screen layout: featured, top-10, and curated rows.
     * Cached privately for 5 minutes.
     */
    @GET
    @Path("/home")
    public Response getHome() {
        return Response.ok(videosService.getHome())
                .cacheControl(privateFor(300))
                .build();
    }

    /**
     * GET /api/videos?q=&category=&ordering=&period=&page=
     * Full-text and category search with pagination. Cached 5 min.
     */
    @GET
    @Path("/videos")
    public Response listVideos(
            @QueryParam("q")        String q,
            @QueryParam("category") String category,
            @QueryParam("ordering") String ordering,
            @QueryParam("period")   String period,
            @QueryParam("page")     Integer page
    ) {
        VideoListQuery query = new VideoListQuery(q, category, ordering, period, page);
        return Response.ok(videosService.listVideos(query))
                .cacheControl(privateFor(300))
                .build();
    }

    /**
     * GET /api/videos/:id
     * Full video detail including embed URL. Cached 5 min.
     * Returns 404 for blocked videos (the block is invisible to callers).
     */
    @GET
    @Path("/videos/{id}")
    public Response getVideo(@PathParam("id") String id) {
        return Response.ok(videosService.getVideo(id))
                .cacheControl(privateFor(300))
                .build();
    }

    /**
     * GET /api/categories
     * Full category list. Cached 24 hours (categories change rarely).
     */
    @GET
    @Path("/categories")
    public Response getCategories() {
        return Response.ok(videosService.getCategories())
                .cacheControl(privateFor(86400))
                .build();
    }

    // -------------------------------------------------------------------------

    private CacheControl privateFor(int seconds) {
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.setMaxAge(seconds);
        return cc;
    }
}
