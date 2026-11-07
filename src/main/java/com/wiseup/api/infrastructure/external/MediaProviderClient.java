package com.wiseup.api.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiseup.api.infrastructure.cache.InMemoryCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * HTTP client for the external upstream media provider API.
 *
 * <p>All GET calls go through the in-memory cache:
 * <ul>
 *   <li>fresh hit  → return cached value immediately</li>
 *   <li>upstream OK → update cache, return fresh value</li>
 *   <li>upstream error + stale entry within ceiling → return stale value</li>
 *   <li>upstream error + no entry or entry too old → throw exception</li>
 * </ul>
 */
@ApplicationScoped
public class MediaProviderClient {

    private static final Logger LOG = Logger.getLogger(MediaProviderClient.class);
    private static final String USER_AGENT = "be-wiseup/1.0 (Quarkus)";

    private static final List<String> ALLOWED_PARAMS = List.of(
            "search", "category", "ordering", "period", "page", "thumbsize", "id"
    );

    @Inject
    InMemoryCache cache;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "wiseup.upstream.base-url")
    String baseUrl;

    @ConfigProperty(name = "wiseup.upstream.timeout-ms")
    long timeoutMs;

    @ConfigProperty(name = "wiseup.cache.max-stale-seconds")
    int maxStaleSeconds;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // -------------------------------------------------------------------------

    public JsonNode search(Map<String, String> params) {
        var allParams = new HashMap<>(params);
        allParams.put("thumbsize", "large_hd");
        return get("search", allParams);
    }

    public JsonNode videoById(String id) {
        return get("video_by_id", Map.of("id", id, "thumbsize", "large_hd"));
    }

    public JsonNode categories() {
        return get("categories", Map.of());
    }

    /**
     * Retrieves the list of deleted content IDs from the upstream provider.
     * Returns an empty list (not an exception) on network/format errors.
     */
    public List<String> deletedVideos(int page) {
        try {
            JsonNode data = get("deleted", Map.of("page", String.valueOf(page)));
            List<String> ids = new ArrayList<>();
            if (data != null && data.has("videos") && data.get("videos").isArray()) {
                for (JsonNode item : data.get("videos")) {
                    String vid = item.isTextual()
                            ? item.asText()
                            : (item.has("video_id") ? item.get("video_id").asText() : null);
                    if (vid != null && !vid.isBlank()) ids.add(vid);
                }
            }
            return ids;
        } catch (Exception e) {
            LOG.warn("deletedVideos page " + page + " failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if a media item still exists on the upstream.
     * Returns {@link Optional#empty()} on network errors (unknown state).
     */
    public Optional<Boolean> isVideoActive(String id) {
        try {
            JsonNode data = videoById(id);
            return Optional.of(data != null
                    && data.has("video")
                    && data.get("video").has("video_id"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------

    private JsonNode get(String endpoint, Map<String, String> params) {
        String url = buildUrl(endpoint, params);
        Optional<InMemoryCache.CacheEntry<JsonNode>> cached = cache.get(url);

        // Fresh cache hit – skip the network call entirely
        if (cached.isPresent() && cached.get().fresh()) {
            return cached.get().value();
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return handleUpstreamError(url, cached, "HTTP " + resp.statusCode());
            }

            JsonNode body = mapper.readTree(resp.body());
            cache.put(url, body);
            return body;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return handleUpstreamError(url, cached, e.getMessage());
        }
    }

    private JsonNode handleUpstreamError(String url,
                                         Optional<InMemoryCache.CacheEntry<JsonNode>> cached,
                                         String reason) {
        if (cached.isPresent()) {
            int age = cached.get().ageSeconds();
            if (age <= maxStaleSeconds) {
                LOG.warn("Upstream error (" + reason + "); serving stale cache (age=" + age + "s) for " + url);
                return cached.get().value();
            } else {
                LOG.warn("Upstream error (" + reason + "); stale cache too old (" + age + "s), evicting");
                cache.evict(url);
            }
        }
        throw new UpstreamException("Upstream unavailable: " + reason);
    }

    private String buildUrl(String endpoint, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl.replaceAll("/$", ""))
                .append('/').append(endpoint);
        boolean first = true;
        for (String key : ALLOWED_PARAMS) {
            String val = params.get(key);
            if (val != null && !val.isBlank()) {
                sb.append(first ? '?' : '&').append(key).append('=')
                  .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
                first = false;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    public static class UpstreamException extends RuntimeException {
        public UpstreamException(String message) {
            super(message);
        }
    }
}
