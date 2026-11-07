package com.wiseup.api.infrastructure.external;

import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.Optional;

/**
 * Verifies a reCAPTCHA v2 token against Google's siteverify endpoint.
 *
 * <p>When no secret is configured:
 * <ul>
 *   <li>In production: always returns {@code false} (fail-secure).</li>
 *   <li>In dev/test: always returns {@code true} with a warning.</li>
 * </ul>
 */
@ApplicationScoped
public class RecaptchaVerifier {

    private static final Logger LOG = Logger.getLogger(RecaptchaVerifier.class);

    @ConfigProperty(name = "wiseup.captcha.secret")
    Optional<String> secret;

    @ConfigProperty(name = "wiseup.captcha.verify-url")
    String verifyUrl;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile boolean warnedOnce = false;

    public boolean verify(String token) {
        if (secret.isEmpty() || secret.get().isBlank()) {
            if ("dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                if (!warnedOnce) {
                    LOG.warn("No RECAPTCHA_SECRET configured – captcha check bypassed (dev/test only).");
                    warnedOnce = true;
                }
                return true;
            }
            LOG.error("No RECAPTCHA_SECRET configured in production – all captcha checks will fail.");
            return false;
        }

        if (token == null || token.length() < 20 || token.length() > 4000) return false;

        String body = "secret=" + URLEncoder.encode(secret.get(), StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("User-Agent", "be-wiseup/1.0 (Quarkus)")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;

            // Parse "success": true without a full JSON library dependency
            return resp.body().contains("\"success\":true")
                    || resp.body().contains("\"success\": true");

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("reCAPTCHA verification failed: " + e.getMessage());
            // Fail-secure: when the verifier is unreachable, block the login.
            return false;
        }
    }

    /** True when the application is configured to require captcha. */
    public boolean isRequired() {
        return secret.isPresent() && !secret.get().isBlank();
    }
}
