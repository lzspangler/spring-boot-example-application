package com.redhat.lightwell.functional;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class CustomerFT extends BaseFunctionalTest {

    @Test
    void shouldCreateAndRetrieveCustomer() {
        String uniqueEmail = "ft-" + UUID.randomUUID() + "@example.com";

        // ---- Create customer -------------------------------------------------
        Integer createdId = given()
                .spec(spec)
                .body("{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"" + uniqueEmail + "\",\"phoneNumber\":\"555-1234\"}")
                .when()
                .post("/api/customers")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("email", equalTo(uniqueEmail))
                .extract()
                .path("id");

        // ---- Retrieve the same customer --------------------------------------
        given()
                .spec(spec)
                .when()
                .get("/api/customers/{id}", createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(createdId))
                .body("email", equalTo(uniqueEmail));
    }

    @Test
    void shouldRejectUnauthenticatedAccess() {
        // Bypass authentication by not providing any auth spec
        given()
                .baseUri(RestAssured.baseURI)
                .when()
                .get("/api/customers")
                .then()
                .statusCode(401);
    }
}

