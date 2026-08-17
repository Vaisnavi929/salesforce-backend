package com.salesforce.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Service
public class SalesforceCrudService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${salesforce.api-version}")
    private String apiVersion;

    public SalesforceCrudService() {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    // SESSION

    private String getAccessToken(HttpSession session) {

        String accessToken =
                (String) session.getAttribute(
                        "salesforce_access_token"
                );

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Salesforce authentication required. Please login first."
            );
        }

        return accessToken;
    }

    private String getInstanceUrl(HttpSession session) {

        String instanceUrl =
                (String) session.getAttribute(
                        "salesforce_instance_url"
                );

        if (instanceUrl == null || instanceUrl.isBlank()) {
            throw new IllegalStateException(
                    "Salesforce instance URL is missing. Please login again."
            );
        }

        return instanceUrl;
    }

    private String getBaseUrl(HttpSession session) {

        String instanceUrl = getInstanceUrl(session);

        if (instanceUrl.endsWith("/")) {
            instanceUrl =
                    instanceUrl.substring(
                            0,
                            instanceUrl.length() - 1
                    );
        }

        return instanceUrl
                + "/services/data/"
                + apiVersion;
    }

    // GET RECORDS

    public JsonNode getRecords(
            HttpSession session,
            String objectName,
            int page,
            int size
    ) {

        validateObject(objectName);

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 20;
        }

        // Maximum 20 records per request
        size = Math.min(size, 20);

        int offset = page * size;

        String fields = getFields(objectName);

        String soql =
                "SELECT "
                        + fields
                        + " FROM "
                        + objectName
                        + " ORDER BY CreatedDate DESC "
                        + "LIMIT "
                        + size
                        + " OFFSET "
                        + offset;

        /*
         * IMPORTANT:
         *
         * Do NOT use URLEncoder.encode() here.
         *
         * UriComponentsBuilder performs the URL encoding exactly once.
         */
        URI uri =
                UriComponentsBuilder
                        .fromUriString(
                                getBaseUrl(session)
                                        + "/query"
                        )
                        .queryParam("q", soql)
                        .build()
                        .encode()
                        .toUri();

        System.out.println();
        System.out.println("==========================================");
        System.out.println("SALESFORCE QUERY");
        System.out.println("Object : " + objectName);
        System.out.println("Page   : " + page);
        System.out.println("Size   : " + size);
        System.out.println("Offset : " + offset);
        System.out.println("SOQL   : " + soql);
        System.out.println("URI    : " + uri);
        System.out.println("==========================================");

        String response =
                restClient
                        .get()
                        .uri(uri)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + getAccessToken(session)
                        )
                        .header(
                                HttpHeaders.ACCEPT,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .retrieve()
                        .body(String.class);

        return parseResponse(response);
    }

    // GET SINGLE RECORD

    public JsonNode getRecord(
            HttpSession session,
            String objectName,
            String id
    ) {

        validateObject(objectName);
        validateId(id);

        String url =
                getBaseUrl(session)
                        + "/sobjects/"
                        + objectName
                        + "/"
                        + id;

        String response =
                restClient
                        .get()
                        .uri(url)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + getAccessToken(session)
                        )
                        .header(
                                HttpHeaders.ACCEPT,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .retrieve()
                        .body(String.class);

        return parseResponse(response);
    }

    // CREATE RECORD

    public JsonNode createRecord(
            HttpSession session,
            String objectName,
            Map<String, Object> record
    ) {

        validateObject(objectName);

        if (record == null || record.isEmpty()) {
            throw new IllegalArgumentException(
                    "Record data is required."
            );
        }

        String url =
                getBaseUrl(session)
                        + "/sobjects/"
                        + objectName;

        String response =
                restClient
                        .post()
                        .uri(url)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + getAccessToken(session)
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(record)
                        .retrieve()
                        .body(String.class);

        return parseResponse(response);
    }

    // UPDATE RECORD

    public JsonNode updateRecord(
            HttpSession session,
            String objectName,
            String id,
            Map<String, Object> record
    ) {

        validateObject(objectName);
        validateId(id);

        if (record == null || record.isEmpty()) {
            throw new IllegalArgumentException(
                    "Record data is required."
            );
        }

        String url =
                getBaseUrl(session)
                        + "/sobjects/"
                        + objectName
                        + "/"
                        + id;

        String response =
                restClient
                        .patch()
                        .uri(url)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + getAccessToken(session)
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(record)
                        .retrieve()
                        .body(String.class);

        /*
         * Salesforce normally returns HTTP 204
         * with an empty response body for PATCH.
         */
        if (response == null || response.isBlank()) {
            return objectMapper.createObjectNode();
        }

        return parseResponse(response);
    }

    // DELETE RECORD

    public void deleteRecord(
            HttpSession session,
            String objectName,
            String id
    ) {

        validateObject(objectName);
        validateId(id);

        String url =
                getBaseUrl(session)
                        + "/sobjects/"
                        + objectName
                        + "/"
                        + id;

        restClient
                .delete()
                .uri(url)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer "
                                + getAccessToken(session)
                )
                .retrieve()
                .toBodilessEntity();
    }

    // VALIDATE OBJECT

    private void validateObject(String objectName) {

        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException(
                    "Salesforce object is required."
            );
        }

        switch (objectName) {

            case "Account":
            case "Opportunity":
            case "Lead":
            case "Contact":
            case "Case":
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported Salesforce object: "
                                + objectName
                );
        }
    }

    // VALIDATE ID

    private void validateId(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Record ID is required."
            );
        }
    }

    // FIELDS

    private String getFields(String objectName) {

        return switch (objectName) {

            case "Account" ->
                    "Id,Name,Industry,Phone,Website";

            case "Opportunity" ->
                    "Id,Name,StageName,Amount,CloseDate,AccountId";

            case "Lead" ->
                    "Id,FirstName,LastName,Company,Status,Email";

            case "Contact" ->
                    "Id,FirstName,LastName,Email,Phone,AccountId";

            case "Case" ->
                    "Id,CaseNumber,Subject,Status,Priority,Origin";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported Salesforce object: "
                                    + objectName
                    );
        };
    }

    // PARSE RESPONSE

    private JsonNode parseResponse(String response) {

        if (response == null || response.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {

            return objectMapper.readTree(response);

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to parse Salesforce API response: "
                            + e.getMessage(),
                    e
            );
        }
    }
}