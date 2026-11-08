package com.redhat.lightwell.functional;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class AdminFT extends BaseFunctionalTest {

    /**
     * These tests run with the default {@code user} credentials.
     * The admin‑only endpoints must reject the request with 403.
     */
    @Test
    void shouldRejectUserRoleOnAdminEndpoints() {
        // Config import – should be forbidden for non‑admin
        given()
                .spec(spec)
                .body("key: value")
                .when()
                .post("/api/admin/config")
                .then()
                .statusCode(403);

        // Config read – should be forbidden for non‑admin
        given()
                .spec(spec)
                .when()
                .get("/api/admin/config")
                .then()
                .statusCode(403);
    }

    /**
     * Admin‑only operations using the admin credentials.
     */
    @Test
    void adminShouldImportConfigAndQueryData() {
        // 1️⃣ Import a tiny YAML configuration
        given()
                .spec(spec)
                .auth()
                .preemptive()
                .basic("admin", "admin123")
                .contentType("text/x-yaml")
                .body("featureEnabled: true")
                .when()
                .post("/api/admin/config")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("keysImported", equalTo(1));

        // 2️⃣ Retrieve the configuration and verify the key is present
        given()
                .spec(spec)
                .auth()
                .preemptive()
                .basic("admin", "admin123")
                .when()
                .get("/api/admin/config")
                .then()
                .statusCode(200)
                .body("featureEnabled", equalTo(true));

        // 3️⃣ Execute a JSON‑Path query against a simple payload
        String jsonPayload = "{\"account\":{\"balance\":12345}}";
        String expression = "$.account.balance";

        given()
                .spec(spec)
                .auth()
                .preemptive()
                .basic("admin", "admin123")
                .contentType(ContentType.JSON)
                .body("{\"data\":\"" + jsonPayload.replace("\"", "\\\"") + "\",\"expression\":\"" + expression + "\"}")
                .when()
                .post("/api/admin/query")
                .then()
                .statusCode(200)
                // The result may be returned as a number or a string; accept both
                .body("expression", equalTo(expression))
                .body("result", anyOf(equalTo("12345"), equalTo(12345)))
                // resultType can be "String" or "Integer" depending on JSON parser handling
                .body("resultType", anyOf(equalTo("String"), equalTo("Integer")));
    }
}

