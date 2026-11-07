package com.wiseup.api;

import com.wiseup.api.infrastructure.security.Crypto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Crypto utilities.
 */
class CryptoTest {

    @Test
    void hashAndVerifyRoundTrip() {
        String password = "s3cr3t!Password";
        String hash = Crypto.hash(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("PBKDF2$SHA512$"));
        assertTrue(Crypto.verify(password, hash));
    }

    @Test
    void wrongPasswordFails() {
        String hash = Crypto.hash("correct-password");
        assertFalse(Crypto.verify("wrong-password", hash));
    }

    @Test
    void sha256HexIsConsistent() {
        String a = Crypto.sha256Hex("token123");
        String b = Crypto.sha256Hex("token123");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void sha256HexDiffersForDifferentInputs() {
        assertNotEquals(Crypto.sha256Hex("abc"), Crypto.sha256Hex("xyz"));
    }

    @Test
    void randomTokenHasExpectedLength() {
        String token = Crypto.randomToken();
        // 32 bytes → 43 chars base64url without padding
        assertTrue(token.length() >= 42, "Token too short: " + token.length());
        assertFalse(token.contains("="), "Token should not contain padding");
    }

    @Test
    void twoRandomTokensAreDifferent() {
        assertNotEquals(Crypto.randomToken(), Crypto.randomToken());
    }
}
