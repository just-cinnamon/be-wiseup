package com.wiseup.api.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wiseup.api.application.dto.*;
import com.wiseup.api.application.mapper.VideoMapper;
import com.wiseup.api.infrastructure.external.MediaProviderClient;
import com.wiseup.api.presentation.exception.InvalidParamsException;
import com.wiseup.api.presentation.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Catalogue domain service – fetches, maps and filters video content.
 *
 * <p>Blocked videos are filtered here, in one place, so home / list / detail
 * all share the same behaviour automatically.
 */
@ApplicationScoped
public class VideosService {

    private static final Pattern ID_PATTERN       = Pattern.compile("^[A-Za-z0-9_]{1,40}$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9\\-]{1,60}$");

    private static final List<String> ORDERINGS = List.of("featured", "newest", "mostviewed", "rating");
    private static final List<String> PERIODS   = List.of("weekly", "monthly", "alltime");

    // Home section definitions
    private static final Map<String, String> FEATURED_PARAMS =
            Map.of("ordering", "rating", "period", "weekly");
    private static final Map<String, String> TOP10_PARAMS =
            Map.of("ordering", "mostviewed", "period", "weekly");

    private static final List<HomeRowDef> HOME_ROWS = List.of(
            new HomeRowDef("em-alta", "Em alta na semana",
                    Map.of("ordering", "mostviewed", "period", "weekly")),
            new HomeRowDef("lancamentos", "Lançamentos",
                    Map.of("ordering", "newest")),
            new HomeRowDef("mais-bem-avaliados", "Mais bem avaliados",
                    Map.of("ordering", "rating", "period", "monthly")),
            new HomeRowDef("cat-amateur", "Amador",
                    Map.of("category", "amateur", "ordering", "mostviewed", "period", "alltime")),
            new HomeRowDef("cat-brazilian", "Brasileiros",
                    Map.of("category", "brazilian", "ordering", "mostviewed", "period", "alltime")),
            new HomeRowDef("cat-romantic", "Romântico",
                    Map.of("category", "romantic", "ordering", "mostviewed", "period", "alltime"))
    );

    @Inject
    MediaProviderClient client;

    @Inject
    ModerationService moderation;

    @Inject
    VideoMapper mapper;

    // Virtual thread executor for parallel home-row fetching
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public HomeResponse getHome() {
        // Fetch featured, top10, and all rows in parallel
        List<CompletableFuture<List<TitleDto>>> futures = new ArrayList<>();
        futures.add(fetchList(FEATURED_PARAMS));
        futures.add(fetchList(TOP10_PARAMS));
        for (HomeRowDef row : HOME_ROWS) {
            futures.add(fetchList(row.params()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<TitleDto> featuredList = safeGet(futures.get(0));
        List<TitleDto> top10List    = safeGet(futures.get(1));

        TitleDto featured = featuredList.isEmpty() ? null
                : new TitleDto(featuredList.get(0), 1);

        List<HomeRowResponse> rows = new ArrayList<>();
        for (int i = 0; i < HOME_ROWS.size(); i++) {
            HomeRowDef def = HOME_ROWS.get(i);
            rows.add(new HomeRowResponse(def.id(), def.title(), safeGet(futures.get(2 + i))));
        }

        return new HomeResponse(featured, top10List.stream().limit(10).collect(Collectors.toList()), rows);
    }

    public VideoListResponse listVideos(VideoListQuery query) {
        Map<String, String> params = parseListQuery(query);
        int page = Integer.parseInt(params.getOrDefault("page", "1"));
        List<TitleDto> items = fetchListSync(params);
        return new VideoListResponse(page, items, !items.isEmpty() && page < 50);
    }

    public TitleDetailDto getVideo(String id) {
        if (!ID_PATTERN.matcher(id).matches()) throw new NotFoundException();
        if (moderation.isBlocked(id)) throw new NotFoundException();

        JsonNode data = client.videoById(id);
        if (data == null || !data.has("video") || !data.get("video").has("video_id")) {
            throw new NotFoundException();
        }
        return mapper.toTitleDetail(data.get("video"));
    }

    public List<CategoryDto> getCategories() {
        JsonNode data = client.categories();
        List<CategoryDto> result = new ArrayList<>();
        if (data != null && data.has("categories") && data.get("categories").isArray()) {
            for (JsonNode item : data.get("categories")) {
                result.add(mapper.toCategory(item));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private CompletableFuture<List<TitleDto>> fetchList(Map<String, String> params) {
        return CompletableFuture.supplyAsync(() -> fetchListSync(params), executor);
    }

    private List<TitleDto> fetchListSync(Map<String, String> params) {
        try {
            JsonNode data = client.search(params);
            List<TitleDto> result = new ArrayList<>();
            if (data != null && data.has("videos") && data.get("videos").isArray()) {
                for (JsonNode v : data.get("videos")) {
                    TitleDto title = mapper.toTitle(v);
                    if (!moderation.isBlocked(title.id())) {
                        result.add(title);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseListQuery(VideoListQuery q) {
        String search   = q.q()        == null ? "" : q.q().trim();
        String category = q.category() == null ? "" : q.category().trim();
        String ordering = q.ordering() == null ? "mostviewed" : q.ordering();
        String period   = q.period()   == null ? "weekly"     : q.period();
        int    page     = q.page()     == null ? 1            : q.page();

        if (search.length() > 80)
            throw new InvalidParamsException("A busca pode ter no máximo 80 caracteres.");
        if (!category.isBlank() && !CATEGORY_PATTERN.matcher(category).matches())
            throw new InvalidParamsException("Categoria inválida.");
        if (!ORDERINGS.contains(ordering))
            throw new InvalidParamsException("ordering deve ser: " + String.join(", ", ORDERINGS) + ".");
        if (!PERIODS.contains(period))
            throw new InvalidParamsException("period deve ser: " + String.join(", ", PERIODS) + ".");
        if (page < 1 || page > 50)
            throw new InvalidParamsException("page deve ser de 1 a 50.");

        Map<String, String> params = new LinkedHashMap<>();
        if (!search.isBlank())   params.put("search",   search);
        if (!category.isBlank()) params.put("category", category);
        params.put("ordering", ordering);
        params.put("period",   period);
        params.put("page",     String.valueOf(page));
        return params;
    }

    private <T> List<T> safeGet(CompletableFuture<List<T>> future) {
        try {
            return future.join();
        } catch (Exception e) {
            return List.of();
        }
    }

    private record HomeRowDef(String id, String title, Map<String, String> params) {}
}
