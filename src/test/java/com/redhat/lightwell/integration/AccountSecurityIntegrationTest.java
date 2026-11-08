package com.redhat.lightwell.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // keep a Hibernate session open for lazy‑loaded associations
class AccountSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verify that an unauthenticated request is rejected with 401.
     */
    @Test
    void whenUnauthenticated_thenGetAccountReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verify that a non‑existent account yields 404 for an authenticated user.
     */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void whenAccountNotFound_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/accounts/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    /**
     * Creating a new account requires authentication but no special role.
     * Use the existing customer (ID 1) created by DataSeeder.
     */
    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void whenAuthenticatedUser_thenCreateAccountSucceeds() throws Exception {
        String requestBody = """
                {
                    "customerId": 1,
                    "accountType": "CHECKING"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.accountNumber", not(emptyOrNullString())))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(1)))
                .andExpect(jsonPath("$.customerName", not(emptyOrNullString())));
    }

    /**
     * Ensure that an unauthenticated request to create an account is rejected.
     */
    @Test
    void whenUnauthenticated_thenCreateAccountReturnsUnauthorized() throws Exception {
        String requestBody = """
                {
                    "customerId": 1,
                    "accountType": "SAVINGS"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }
}
