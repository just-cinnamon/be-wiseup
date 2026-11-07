package com.wiseup.api.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with bcrypt-equivalent strength using PBKDF2+HMAC-SHA512.
 *
 * <p>Format stored in DB: {@code PBKDF2$SHA512$<iterations>$<saltB64>$<hashB64>}
 *
 * <p>SHA-256 is used for token hashing (not password hashing).
 */
public final class Crypto {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final int ITERATIONS = 310_000;   // OWASP 2024 recommended minimum
    private static final int KEY_LENGTH  = 256;      // bits
    private static final int SALT_BYTES  = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {}

    /**
     * Hashes a plain-text password. This is intentionally slow (PBKDF2).
     */
    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        return "PBKDF2$SHA512$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /** Convenience overload for String passwords. */
    public static String hash(String password) {
        return hash(password.toCharArray());
    }

    /**
     * Verifies a plain-text password against a stored hash.
     * Always takes the same wall-clock time regardless of match result to
     * prevent timing-based enumeration attacks.
     */
    public static boolean verify(String password, String stored) {
        if (stored == null || !stored.startsWith("PBKDF2$")) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 5) return false;
        try {
            int iter  = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);

            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iter, expected.length * 8);
            byte[] actual = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();
            spec.clearPassword();

            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs a full PBKDF2 computation to consume the same time as
     * {@link #verify} even when the user does not exist (prevents timing oracle).
     */
    public static void fakeVerify(String dummyPassword) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            pbkdf2(dummyPassword.toCharArray(), salt);
        } catch (Exception ignored) {
        }
    }

    // SHA-256 of an opaque bearer token (stored in DB; raw token only on the client).
    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Generates a cryptographically-random URL-safe token (43 chars ~ 256 bits). */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // -------------------------------------------------------------------------

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            byte[] result = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();
            spec.clearPassword();
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
