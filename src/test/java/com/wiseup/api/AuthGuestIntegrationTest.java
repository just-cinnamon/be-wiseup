package com.wiseup.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class AuthGuestIntegrationTest {

    @Test
    @DisplayName("POST /api/auth/guest establishes valid guest session with pre-accepted adult & terms")
    void guestSessionLifecycle() {
        // 1. Request guest session without body
        Response guestRes = given()
                .contentType("application/json")
                .when()
                .post("/api/auth/guest")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("no-store"))
                .body("user.id", notNullValue())
                .body("user.ageVerified", is(true))
                .body("user.termsAccepted", is(true))
                .body("user.isAdmin", is(false))
                .body("user.isGuest", is(true))
                .body("token", notNullValue())
                .body("expiresAt", notNullValue())
                .extract().response();

        String token = guestRes.jsonPath().getString("token");
        String userId = guestRes.jsonPath().getString("user.id");

        // 2. Validate GET /api/auth/me works with guest token
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("user.id", is(userId))
                .body("user.isGuest", is(true))
                .body("user.ageVerified", is(true))
                .body("user.termsAccepted", is(true))
                .body("legalVersions.termos", notNullValue())
                .body("legalVersions.privacidade", notNullValue());

        // 3. Validate catalogue access with guest token (all filters pass)
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/categories")
                .then()
                .statusCode(200);

        // 4. Validate logout invalidates the guest session
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(200)
                .body("ok", is(true));

        // 5. Subsequent access is rejected with 401
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Guest accounts cannot be used with email/password login")
    void guestAccountLoginRejected() {
        Response guestRes = given()
                .contentType("application/json")
                .when()
                .post("/api/auth/guest")
                .then()
                .statusCode(200)
                .extract().response();

        String userId = guestRes.jsonPath().getString("user.id");
        String guestEmail = "guest_" + userId + "@visitor.wiseup.test";

        given()
                .contentType("application/json")
                .body("{\"email\":\"" + guestEmail + "\",\"password\":\"anyPassword123\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("error.code", is("INVALID_CREDENTIALS"));
    }
}
