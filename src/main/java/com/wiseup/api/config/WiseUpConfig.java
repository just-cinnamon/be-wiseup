package com.wiseup.api.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

/**
 * Strongly-typed configuration loaded from application.properties / env-vars.
 * All secrets arrive as {@link Optional} so the app can warn and fail safely
 * when they are absent.
 */
@ConfigMapping(prefix = "wiseup")
public interface WiseUpConfig {

    Upstream upstream();

    Cache cache();

    Session session();

    Auth auth();

    Captcha captcha();

    @WithName("seed-users")
    Optional<String> seedUsers();

    Legal legal();

    @WithName("access-log")
    AccessLog accessLog();

    Sync sync();

    // -------------------------------------------------------------------------

    interface Upstream {
        @WithName("base-url")
        String baseUrl();

        @WithName("embed-base-url")
        Optional<String> embedBaseUrl();

        @WithName("timeout-ms")
        long timeoutMs();
    }

    interface Cache {
        @WithName("ttl-seconds")
        int ttlSeconds();

        @WithName("max-entries")
        int maxEntries();

        @WithName("max-stale-seconds")
        int maxStaleSeconds();
    }

    interface Session {
        @WithName("ttl-hours")
        int ttlHours();
    }

    interface Auth {
        @WithName("max-attempts")
        int maxAttempts();

        @WithName("lock-minutes")
        int lockMinutes();
    }

    interface Captcha {
        Optional<String> secret();

        @WithName("verify-url")
        String verifyUrl();
    }

    interface Legal {
        Map<String, String> versions();
    }

    interface AccessLog {
        @WithName("retention-days")
        int retentionDays();
    }

    interface Sync {
        @WithName("interval-hours")
        int intervalHours();

        int pages();
    }
}
