package com.salesforce.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesforce.backend.service.SalesforceCrudService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/salesforce")
public class SalesforceController {

    private final SalesforceCrudService salesforceCrudService;

    public SalesforceController(
            SalesforceCrudService salesforceCrudService
    ) {
        this.salesforceCrudService = salesforceCrudService;
    }

    /**
     * GET
     *
     * Example:
     * GET /api/salesforce/Account?page=0&size=20
     */
    @GetMapping("/{objectName}")
    public ResponseEntity<?> getRecords(
            @PathVariable String objectName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpSession session
    ) {

        try {

            JsonNode response =
                    salesforceCrudService.getRecords(
                            session,
                            objectName,
                            page,
                            size
                    );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return errorResponse(e);
        }
    }

    /**
     * GET
     *
     * Example:
     * GET /api/salesforce/Account/001XXXXXXXXXXXX
     */
    @GetMapping("/{objectName}/{id}")
    public ResponseEntity<?> getRecord(
            @PathVariable String objectName,
            @PathVariable String id,
            HttpSession session
    ) {

        try {

            JsonNode response =
                    salesforceCrudService.getRecord(
                            session,
                            objectName,
                            id
                    );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return errorResponse(e);
        }
    }

    /**
     * POST
     *
     * Example:
     * POST /api/salesforce/Account
     */
    @PostMapping("/{objectName}")
    public ResponseEntity<?> createRecord(
            @PathVariable String objectName,
            @RequestBody Map<String, Object> record,
            HttpSession session
    ) {

        try {

            JsonNode response =
                    salesforceCrudService.createRecord(
                            session,
                            objectName,
                            record
                    );

            return ResponseEntity
                    .status(201)
                    .body(response);

        } catch (Exception e) {

            return errorResponse(e);
        }
    }

    /**
     * PATCH
     *
     * Example:
     * PATCH /api/salesforce/Account/001XXXXXXXXXXXX
     */
    @PatchMapping("/{objectName}/{id}")
    public ResponseEntity<?> updateRecord(
            @PathVariable String objectName,
            @PathVariable String id,
            @RequestBody Map<String, Object> record,
            HttpSession session
    ) {

        try {

            JsonNode response =
                    salesforceCrudService.updateRecord(
                            session,
                            objectName,
                            id,
                            record
                    );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return errorResponse(e);
        }
    }

    /**
     * DELETE
     *
     * Example:
     * DELETE /api/salesforce/Account/001XXXXXXXXXXXX
     */
    @DeleteMapping("/{objectName}/{id}")
    public ResponseEntity<?> deleteRecord(
            @PathVariable String objectName,
            @PathVariable String id,
            HttpSession session
    ) {

        try {

            salesforceCrudService.deleteRecord(
                    session,
                    objectName,
                    id
            );

            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            true,
                            "message",
                            "Record deleted successfully"
                    )
            );

        } catch (Exception e) {

            return errorResponse(e);
        }
    }

    /**
     * Common error response.
     */
    private ResponseEntity<?> errorResponse(
            Exception e
    ) {

        String message =
                e.getMessage() != null
                        ? e.getMessage()
                        : "Unexpected server error";

        /*
         * Salesforce authentication error.
         */
        if (message.contains(
                "Salesforce authentication required"
        )) {

            return ResponseEntity
                    .status(401)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    "UNAUTHORIZED",
                                    "message",
                                    message
                            )
                    );
        }

        /*
         * Salesforce instance URL missing.
         */
        if (message.contains(
                "Salesforce instance URL is missing"
        )) {

            return ResponseEntity
                    .status(401)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    "SALESFORCE_SESSION_INVALID",
                                    "message",
                                    message
                            )
                    );
        }

        /*
         * Invalid request.
         */
        if (e instanceof IllegalArgumentException) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    "BAD_REQUEST",
                                    "message",
                                    message
                            )
                    );
        }

        /*
         * General server error.
         */
        return ResponseEntity
                .internalServerError()
                .body(
                        Map.of(
                                "success",
                                false,
                                "error",
                                "REQUEST_FAILED",
                                "message",
                                message
                        )
                );
    }
}