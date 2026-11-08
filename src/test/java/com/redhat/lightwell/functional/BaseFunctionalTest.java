package com.redhat.lightwell.functional;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for all functional (FT) tests.
 * It reads the base URL and credentials from environment variables
 * (or system properties as a fallback) and configures a RestAssured
 * {@link RequestSpecification} that is reused by subclasses.
 *
 * The specification includes:
 * - Base URI (APP_BASE_URL)
 * - Pre‑emptive Basic authentication (APP_USERNAME / APP_PASSWORD)
 * - Default {@code Content-Type: application/json}
 *
 * Individual tests can override the content type when needed (e.g. XML
 * uploads) while retaining the authentication and base URI.
 */
public abstract class BaseFunctionalTest {

    protected static RequestSpecification spec;

    @BeforeAll
    public static void setUpSpec() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = System.getProperty("APP_BASE_URL", "http://localhost:8080");
        }

        String username = System.getenv("APP_USERNAME");
        if (username == null || username.isBlank()) {
            username = System.getProperty("APP_USERNAME", "user");
        }

        String password = System.getenv("APP_PASSWORD");
        if (password == null || password.isBlank()) {
            password = System.getProperty("APP_PASSWORD", "user123");
        }

        RestAssured.baseURI = baseUrl;

        PreemptiveBasicAuthScheme auth = new PreemptiveBasicAuthScheme();
        auth.setUserName(username);
        auth.setPassword(password);

        spec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setAuth(auth)
                .setContentType(ContentType.JSON) // default for JSON payloads
                .build();
    }
}

