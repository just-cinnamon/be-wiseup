package com.wiseup.api.application.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.wiseup.api.application.dto.CategoryDto;
import com.wiseup.api.application.dto.TitleDetailDto;
import com.wiseup.api.application.dto.TitleDto;
import com.wiseup.api.config.WiseUpConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps upstream media provider JSON objects to internal DTO contracts.
 */
@ApplicationScoped
public class VideoMapper {

    private static final Pattern NUMERIC_RANGE = Pattern.compile("^\\d+(-\\d+)*$");
    private static final long THIRTY_DAYS_MS   = 30L * 24 * 60 * 60 * 1_000;

    private static final Map<String, String> HTML_ENTITIES = Map.of(
            "amp",  "&",
            "lt",   "<",
            "gt",   ">",
            "quot", "\"",
            "apos", "'",
            "#39",  "'",
            "nbsp", " "
    );

    @Inject
    WiseUpConfig config;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public TitleDto toTitle(JsonNode video) {
        OffsetDateTime published = parseDate(video);
        int views    = getInt(video, "views");
        List<String> tags    = tagNames(video).stream().limit(3).toList();
        String image = biggestThumb(video);

        String description = buildDescription(views, tags);

        return new TitleDto(
                text(video, "video_id"),
                decodeHtml(text(video, "title")),
                "movie",
                image,
                image,
                published != null ? published.getYear() : null,
                "18",
                formatDuration(text(video, "duration")),
                genres(video),
                match(video),
                description,
                published != null && (Instant.now().toEpochMilli() - published.toInstant().toEpochMilli() < THIRTY_DAYS_MS)
        );
    }

    public TitleDetailDto toTitleDetail(JsonNode video) {
        TitleDto base = toTitle(video);
        OffsetDateTime published = parseDate(video);
        String videoId = text(video, "video_id");
        String embedBase = config.upstream().embedBaseUrl()
                .orElse("https://www.pornhub.com/embed");

        String embedUrl = embedBase.replaceAll("/$", "") + "/" + encode(videoId);

        return new TitleDetailDto(
                base.id(), base.title(), base.type(), base.backdrop(), base.poster(),
                base.year(), base.maturity(), base.duration(), base.genres(),
                base.match(), base.description(), base.isNew(),
                tagNames(video),
                getInt(video, "views"),
                published != null ? published.toLocalDate().toString() : null,
                embedUrl
        );
    }

    public CategoryDto toCategory(JsonNode item) {
        String slug = text(item, "category");
        return new CategoryDto(text(item, "id"), slug, formatCategory(slug));
    }

    // -------------------------------------------------------------------------
    // Text and Formatting Helpers
    // -------------------------------------------------------------------------

    /** Decodes numeric and named HTML entities. */
    public static String decodeHtml(String text) {
        if (text == null || text.isEmpty()) return "";

        Pattern numericRef = Pattern.compile("&#(\\d+);");
        Matcher m = numericRef.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                int cp = Integer.parseInt(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) cp)));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);

        Pattern namedRef = Pattern.compile("&([a-zA-Z]+|#39);");
        Matcher m2 = namedRef.matcher(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        while (m2.find()) {
            String name = m2.group(1).toLowerCase();
            String replacement = HTML_ENTITIES.getOrDefault(name, m2.group(0));
            m2.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
        }
        m2.appendTail(sb2);

        return sb2.toString();
    }

    /** "12:34" → "12min" · "1:05:20" → "1h 05min" · "0:45" → "1min" */
    public static String formatDuration(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.split(":");
        if (parts.length < 2) return "";
        try {
            int h, m, s;
            if (parts.length == 3) {
                h = Integer.parseInt(parts[0]);
                m = Integer.parseInt(parts[1]);
                s = Integer.parseInt(parts[2]);
            } else {
                h = 0;
                m = Integer.parseInt(parts[0]);
                s = Integer.parseInt(parts[1]);
            }
            int minutes = Math.max(1, (int) Math.floor(h * 60 + m + s / 60.0));
            int hours   = minutes / 60;
            int rest    = minutes % 60;
            if (hours > 0) return hours + "h " + String.format("%02d", rest) + "min";
            return rest + "min";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * Converts hyphen-separated slugs to Title Case.
     * Strips duplicate disambiguation "-1" suffix used by upstream provider.
     */
    public static String formatCategory(String slug) {
        if (slug == null || slug.isEmpty()) return "";
        String base = slug.replaceAll("-1$", "");
        if (NUMERIC_RANGE.matcher(base).matches()) return base;
        String[] words = base.split("-");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** Formats view count: 1234567 → "1,2 mi" · 5400 → "5,4 mil" */
    public static String formatViews(long n) {
        if (n >= 1_000_000) return String.format("%.1f mi", n / 1_000_000.0).replace('.', ',');
        if (n >= 1_000)     return String.format("%.1f mil", n / 1_000.0).replace('.', ',');
        return String.valueOf(n);
    }

    // -------------------------------------------------------------------------

    private OffsetDateTime parseDate(JsonNode video) {
        String raw = text(video, "publish_date");
        if (raw == null || raw.isBlank()) return null;
        try {
            String iso = raw.replace(' ', 'T') + "Z";
            return OffsetDateTime.parse(iso, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String biggestThumb(JsonNode video) {
        if (video.has("thumbs") && video.get("thumbs").isArray()) {
            JsonNode best = null;
            int bestWidth = 0;
            for (JsonNode t : video.get("thumbs")) {
                int w = t.has("width") ? t.get("width").asInt() : 0;
                if (w > bestWidth) { bestWidth = w; best = t; }
            }
            if (best != null && best.has("src")) return best.get("src").asText();
        }
        if (video.has("default_thumb")) return video.get("default_thumb").asText();
        if (video.has("thumb"))         return video.get("thumb").asText();
        return "";
    }

    private List<String> tagNames(JsonNode video) {
        List<String> names = new ArrayList<>();
        if (video.has("tags") && video.get("tags").isArray()) {
            for (JsonNode t : video.get("tags")) {
                String name = t.has("tag_name") ? t.get("tag_name").asText()
                        : (t.isTextual() ? t.asText() : null);
                if (name != null && !name.isBlank()) names.add(name);
            }
        }
        return names;
    }

    private List<String> genres(JsonNode video) {
        List<String> g = new ArrayList<>();
        if (video.has("categories") && video.get("categories").isArray()) {
            for (JsonNode c : video.get("categories")) {
                String slug = c.has("category") ? c.get("category").asText()
                        : (c.isTextual() ? c.asText() : null);
                if (slug != null && !slug.isBlank()) g.add(formatCategory(slug));
                if (g.size() >= 3) break;
            }
        }
        return g;
    }

    private int match(JsonNode video) {
        if (!video.has("rating")) return 0;
        return Math.min(100, Math.max(0, (int) Math.round(video.get("rating").asDouble())));
    }

    private String buildDescription(long views, List<String> tags) {
        List<String> parts = new ArrayList<>();
        if (views > 0) parts.add(formatViews(views) + " de visualizações");
        if (!tags.isEmpty()) parts.add(String.join(", ", tags));
        return String.join(" · ", parts);
    }

    private String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private int getInt(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asInt() : 0;
    }

    private String encode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
