package com.wiseup.api.domain.service;

import com.wiseup.api.application.dto.*;
import com.wiseup.api.config.WiseUpConfig;
import com.wiseup.api.domain.model.Session;
import com.wiseup.api.domain.model.User;
import com.wiseup.api.domain.repository.UserRepository;
import com.wiseup.api.infrastructure.external.RecaptchaVerifier;
import com.wiseup.api.infrastructure.security.Crypto;
import com.wiseup.api.presentation.exception.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Core authentication domain service.
 *
 * <p>Handles login, session management, age declaration, term acceptance, and
 * account deletion. No HTTP concerns here – those live in the resource layer.
 *
 * <p>All decisions follow the same rules as the original Node implementation
 * (ADR-0011 → ADR-0019 equivalents).
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9._%+\\-]{0,62}@[a-z0-9][a-z0-9.\\-]{0,62}\\.[a-z]{2,24}$");
    private static final int EMAIL_MAX    = 160;
    private static final int PASSWORD_MIN = 4;
    private static final int PASSWORD_MAX = 200;
    private static final int CAPTCHA_MAX  = 4000;
    private static final int TOKEN_MIN    = 20;
    private static final int TOKEN_MAX    = 200;

    @Inject
    UserRepository users;

    @Inject
    RecaptchaVerifier captcha;

    @Inject
    WiseUpConfig config;

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /**
     * Authenticates a user with email + password (+ optional captcha).
     *
     * <p>Order matters:
     * <ol>
     *   <li>Validate input format (cheap, no DB)</li>
     *   <li>Captcha (network, but before scrypt)</li>
     *   <li>Find user by email (DB)</li>
     *   <li>Check lock state (memory from DB row)</li>
     *   <li>Verify password (CPU-expensive scrypt/PBKDF2)</li>
     *   <li>Start session</li>
     * </ol>
     */
    public LoginResponse login(LoginRequest req, String clientIp) {
        validateLoginInput(req);

        if (captcha.isRequired() && !captcha.verify(req.captchaToken())) {
            throw new CaptchaFailedException();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<User> maybeUser = users.findByEmail(req.email().trim().toLowerCase());

        if (maybeUser.isEmpty()) {
            Crypto.fakeVerify(req.password()); // timing parity
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();

        if (user.isGuest()) {
            Crypto.fakeVerify(req.password()); // timing parity
            throw new InvalidCredentialsException();
        }

        if (user.isLocked(now)) {
            throw new TooManyAttemptsException(config.auth().lockMinutes());
        }

        // Lock window expired → reset counter
        int previousFailed = user.lockedUntil() != null ? 0 : user.failedCount();

        if (!Crypto.verify(req.password(), user.passwordHash())) {
            int newCount  = previousFailed + 1;
            boolean lock  = newCount >= config.auth().maxAttempts();
            OffsetDateTime lockUntil = lock
                    ? now.plusMinutes(config.auth().lockMinutes())
                    : null;
            users.setLockState(user.id(), newCount, lockUntil);
            throw lock
                    ? new TooManyAttemptsException(config.auth().lockMinutes())
                    : new InvalidCredentialsException();
        }

        // Reset lock state on success
        if (previousFailed > 0 || user.lockedUntil() != null) {
            users.setLockState(user.id(), 0, null);
        }

        return startSession(user, now);
    }

    // -------------------------------------------------------------------------
    // Session retrieval (called per-request by the security filter)
    // -------------------------------------------------------------------------

    public SessionUser getSessionUser(String rawToken) {
        if (rawToken == null || rawToken.length() < TOKEN_MIN || rawToken.length() > TOKEN_MAX) {
            throw new UnauthorizedException("Sessão inválida. Entre novamente.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        users.removeExpiredSessions(now);

        Map<String, String> versions = config.legal().versions();
        String termosV      = versions.getOrDefault("termos", "");
        String privacidadeV = versions.getOrDefault("privacidade", "");
        int requiredCount   = versions.size();

        Optional<Session> maybeSession = users.findSession(
                Crypto.sha256Hex(rawToken), termosV, privacidadeV);

        if (maybeSession.isEmpty() || maybeSession.get().isExpired(now)) {
            throw new UnauthorizedException();
        }

        Session session = maybeSession.get();
        return new SessionUser(
                session.userId(),
                session.isGuest() ? null : session.email(),
                session.isAgeVerified(),
                session.hasAcceptedAllTerms(requiredCount),
                session.isAdmin(),
                session.isGuest()
        );
    }

    // -------------------------------------------------------------------------
    // Guest visitor session (ADR-0023 alignment)
    // -------------------------------------------------------------------------

    /**
     * Establishes an anonymous visitor session with adult self-declaration and current
     * terms acceptances automatically confirmed for the visitor gate.
     */
    public LoginResponse startGuestSession(String clientIp) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusHours(config.session().ttlHours());
        String rawToken = Crypto.randomToken();
        String tokenHash = Crypto.sha256Hex(rawToken);

        Session session = users.createGuestSession(tokenHash, clientIp, now, expiresAt, config.legal().versions());

        return new LoginResponse(
                new SessionUser(session.userId(), null, true, true, false, true),
                rawToken,
                expiresAt.toString()
        );
    }

    // -------------------------------------------------------------------------
    // /auth/me
    // -------------------------------------------------------------------------

    public MeResponse me(SessionUser user) {
        return new MeResponse(user, config.legal().versions());
    }

    // -------------------------------------------------------------------------
    // Age gate + Terms acceptance
    // -------------------------------------------------------------------------

    public AcceptGateResponse acceptGate(String userId, String ip) {
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        users.declareAdultAndAcceptTerms(userId, ip, at, config.legal().versions());
        return new AcceptGateResponse(true, true, at.toString(),
                Map.copyOf(config.legal().versions()));
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    public void logout(String rawToken) {
        if (rawToken == null) return;
        users.removeSession(Crypto.sha256Hex(rawToken));
    }

    // -------------------------------------------------------------------------
    // Account deletion (LGPD right to erasure)
    // -------------------------------------------------------------------------

    public void deleteAccount(String userId) {
        users.remove(userId);
    }

    // -------------------------------------------------------------------------
    // Seed users (boot-time)
    // -------------------------------------------------------------------------

    public int seedUsers(java.util.List<SeedEntry> entries) {
        int created = 0;
        for (SeedEntry entry : entries) {
            if (users.findByEmail(entry.email()).isEmpty()) {
                users.create(entry.email(), Crypto.hash(entry.password()));
                created++;
            }
        }
        if (created > 0) LOG.info("Seed users: " + created + " account(s) created.");
        return created;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private LoginResponse startSession(User user, OffsetDateTime now) {
        String rawToken  = Crypto.randomToken();
        String tokenHash = Crypto.sha256Hex(rawToken);
        OffsetDateTime expiresAt = now.plusHours(config.session().ttlHours());

        users.createSession(tokenHash, user.id(), expiresAt);

        boolean termsAccepted = hasAcceptedCurrentTerms(user.id());
        return new LoginResponse(
                new SessionUser(user.id(), user.email(),
                        user.isAgeVerified(), termsAccepted, user.isAdmin()),
                rawToken,
                expiresAt.toString()
        );
    }

    private boolean hasAcceptedCurrentTerms(String userId) {
        var acceptances = users.listAcceptances(userId);
        return config.legal().versions().entrySet().stream().allMatch(e ->
                acceptances.stream().anyMatch(a ->
                        a.document().equals(e.getKey()) && a.version().equals(e.getValue()))
        );
    }

    private void validateLoginInput(LoginRequest req) {
        String email    = req.email() == null ? "" : req.email().trim().toLowerCase();
        String password = req.password() == null ? "" : req.password();
        String captchaToken = req.captchaToken() == null ? "" : req.captchaToken();

        if (email.length() > EMAIL_MAX || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidParamsException("Informe um e-mail válido.");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new InvalidParamsException(
                    "A senha deve ter de " + PASSWORD_MIN + " a " + PASSWORD_MAX + " caracteres.");
        }
        if (captchaToken.length() > CAPTCHA_MAX ||
                (captcha.isRequired() && captchaToken.isBlank())) {
            throw new InvalidParamsException("Confirme que você não é um robô.");
        }
    }

    // -------------------------------------------------------------------------

    public record SeedEntry(String email, String password) {}
}
