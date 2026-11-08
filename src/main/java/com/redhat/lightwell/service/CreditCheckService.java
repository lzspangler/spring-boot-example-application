package com.redhat.lightwell.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import com.redhat.lightwell.exception.ResourceNotFoundException;
import com.redhat.lightwell.model.Customer;
import com.redhat.lightwell.model.dto.CreditCheckResponse;
import com.redhat.lightwell.repository.CustomerRepository;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CreditCheckService {

    private static final Logger log = LoggerFactory.getLogger(CreditCheckService.class);

    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final String creditCheckUrl;

    public CreditCheckService(CustomerRepository customerRepository, AuditService auditService,
                               @Value("${app.credit-check.url}") String creditCheckUrl) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.creditCheckUrl = creditCheckUrl;
    }

    public CreditCheckResponse performCreditCheck(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        // The query values are user data (names contain spaces) and MUST be
        // encoded: with a raw space the whole URI is illegal and HttpGet
        // throws IllegalArgumentException before any network I/O, so the
        // catch below silently fell back to the hash-based score -- the
        // external bureau was never called for any customer whose name has
        // a space (i.e., all of them). Found by
        // agentic-test-generation-integration-issue-fpc8f.
        String url = creditCheckUrl + "?name="
                + URLEncoder.encode(customer.getFirstName() + " " + customer.getLastName(), StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode(customer.getEmail(), StandardCharsets.UTF_8);

        int score;
        String status;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    String body = EntityUtils.toString(response.getEntity());
                    score = parseCreditScore(body);
                    status = score >= 700 ? "GOOD" : score >= 500 ? "FAIR" : "POOR";
                } else {
                    score = generateFallbackScore(customer);
                    status = score >= 700 ? "GOOD" : score >= 500 ? "FAIR" : "POOR";
                }
            }
        } catch (Exception e) {
            // This used to be silent -- a swallowed exception here is why a
            // broken credit-check URL looked like a deterministic fallback
            // score and took 12 agent attempts to diagnose.
            log.warn("Credit check for customer {} ({}) failed, using fallback score: {}",
                    customerId, customer.getEmail(), e.toString());
            score = generateFallbackScore(customer);
            status = score >= 700 ? "GOOD" : score >= 500 ? "FAIR" : "POOR";
        }

        auditService.log("CREDIT_CHECK", "Customer", customerId,
                "Credit check for " + customer.getFirstName() + " " + customer.getLastName()
                        + ": score=" + score + ", status=" + status);

        return new CreditCheckResponse(customerId, score, status, "ExternalCreditBureau",
                LocalDateTime.now());
    }

    private int parseCreditScore(String responseBody) {
        try {
            return Integer.parseInt(responseBody.replaceAll("[^0-9]", "").substring(0, 3));
        } catch (Exception e) {
            return 650;
        }
    }

    private int generateFallbackScore(Customer customer) {
        return 600 + Math.abs(customer.getEmail().hashCode() % 200);
    }
}
