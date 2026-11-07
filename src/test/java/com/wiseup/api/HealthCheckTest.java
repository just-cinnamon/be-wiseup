package com.wiseup.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Smoke tests: verifies the application starts and core routes behave correctly.
 */
@QuarkusTest
class HealthCheckTest {

    @Test
    void healthLiveEndpointReturnsUp() {
        given()
            .when().get("/api/health/live")
            .then()
            .statusCode(200);
    }

    @Test
    void unknownRouteReturns404() {
        given()
            .when().get("/api/nonexistent")
            .then()
            .statusCode(404);
    }

    @Test
    void loginWithMissingBodyReturns400() {
        given()
            .contentType("application/json")
            .body("{}")
            .when().post("/api/auth/login")
            .then()
            .statusCode(400)
            .body("error.code", is("INVALID_PARAMS"));
    }

    @Test
    void catalogueRequiresAuth() {
        given()
            .when().get("/api/home")
            .then()
            .statusCode(401);
    }

    @Test
    void videosRequiresAuth() {
        given()
            .when().get("/api/videos")
            .then()
            .statusCode(401);
    }

    @Test
    void categoriesRequiresAuth() {
        given()
            .when().get("/api/categories")
            .then()
            .statusCode(401);
    }

    @Test
    void reportsRequiresAuth() {
        given()
            .contentType("application/json")
            .body("{}")
            .when().post("/api/reports")
            .then()
            .statusCode(401);
    }

    @Test
    void meRequiresAuth() {
        given()
            .when().get("/api/auth/me")
            .then()
            .statusCode(401);
    }
}
