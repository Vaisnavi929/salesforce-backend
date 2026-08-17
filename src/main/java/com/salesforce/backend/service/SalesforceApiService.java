package com.salesforce.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SalesforceApiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SalesforceApiService() {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes a GET request against Salesforce REST API.
     */
    public JsonNode get(
            String instanceUrl,
            String accessToken,
            String endpoint
    ) {

        String url = buildUrl(instanceUrl, endpoint);

        ResponseEntity<String> response = restClient
                .get()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .header(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .retrieve()
                .toEntity(String.class);

        return parseResponse(response);
    }

    /**
     * Executes a POST request against Salesforce REST API.
     */
    public JsonNode post(
            String instanceUrl,
            String accessToken,
            String endpoint,
            Object body
    ) {

        String url = buildUrl(instanceUrl, endpoint);

        ResponseEntity<String> response = restClient
                .post()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        return parseResponse(response);
    }

    /**
     * Executes a PATCH request against Salesforce REST API.
     */
    public JsonNode patch(
            String instanceUrl,
            String accessToken,
            String endpoint,
            Object body
    ) {

        String url = buildUrl(instanceUrl, endpoint);

        ResponseEntity<String> response = restClient
                .patch()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        return parseResponse(response);
    }

    /**
     * Executes a DELETE request against Salesforce REST API.
     */
    public void delete(
            String instanceUrl,
            String accessToken,
            String endpoint
    ) {

        String url = buildUrl(instanceUrl, endpoint);

        restClient
                .delete()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Builds the complete Salesforce API URL.
     */
    private String buildUrl(
            String instanceUrl,
            String endpoint
    ) {

        if (instanceUrl == null || instanceUrl.isBlank()) {
            throw new IllegalStateException(
                    "Salesforce instance URL is missing"
            );
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException(
                    "Salesforce API endpoint is missing"
            );
        }

        String baseUrl = instanceUrl.endsWith("/")
                ? instanceUrl.substring(
                        0,
                        instanceUrl.length() - 1
                )
                : instanceUrl;

        String path = endpoint.startsWith("/")
                ? endpoint
                : "/" + endpoint;

        return baseUrl + path;
    }

    /**
     * Converts Salesforce JSON response into JsonNode.
     */
    private JsonNode parseResponse(
            ResponseEntity<String> response
    ) {

        String body = response.getBody();

        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(body);

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to parse Salesforce API response: "
                            + e.getMessage(),
                    e
            );
        }
    }
}