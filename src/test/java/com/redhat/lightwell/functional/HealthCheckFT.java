package com.redhat.lightwell.functional;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class HealthCheckFT extends BaseFunctionalTest {

    @Test
    void shouldReportApplicationHealth() {
        // Health endpoint is publicly accessible (no auth required)
        given()
                .baseUri(RestAssured.baseURI)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("UP"));
    }
}

