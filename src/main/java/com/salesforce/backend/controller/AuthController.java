package com.salesforce.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesforce.backend.service.SalesforceOAuthService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SalesforceOAuthService salesforceOAuthService;

    public AuthController(SalesforceOAuthService salesforceOAuthService) {
        this.salesforceOAuthService = salesforceOAuthService;
    }

    /**
     *
     * Browser:
     * http://localhost:8080/auth/login
     *
     * This endpoint redirects the user to Salesforce.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpSession session) {

        String authorizationUrl =
                salesforceOAuthService.createAuthorizationUrl(session);

        System.out.println("==========================================");
        System.out.println("Starting Salesforce OAuth");
        System.out.println("Session ID: " + session.getId());
        System.out.println("Authorization URL:");
        System.out.println(authorizationUrl);
        System.out.println("==========================================");

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(authorizationUrl));

        return ResponseEntity
                .status(302)
                .headers(headers)
                .build();
    }

    /**
     * Salesforce OAuth callback.

     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(
                    name = "error_description",
                    required = false
            ) String errorDescription,
            HttpSession session
    ) {

        System.out.println();
        System.out.println("==========================================");
        System.out.println("SALESFORCE OAUTH CALLBACK");
        System.out.println("==========================================");
        System.out.println("Session ID       : " + session.getId());
        System.out.println("Authorization Code: " + code);
        System.out.println("State            : " + state);
        System.out.println("Error            : " + error);
        System.out.println("Error Description: " + errorDescription);
        System.out.println("==========================================");

        /*
         * Salesforce rejected the authorization request.
         */
        if (error != null && !error.isBlank()) {

            Map<String, Object> response = new HashMap<>();

            response.put("success", false);
            response.put("error", error);
            response.put(
                    "error_description",
                    errorDescription != null
                            ? errorDescription
                            : "Salesforce OAuth authorization failed"
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        /*
         * Callback was opened directly or Salesforce did not
         * provide an authorization code.
         */
        if (code == null || code.isBlank()) {

            Map<String, Object> response = new HashMap<>();

            response.put("success", false);
            response.put(
                    "error",
                    "Authorization code is missing"
            );
            response.put(
                    "message",
                    "Do not open /auth/callback directly. "
                            + "Start OAuth from /auth/login."
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        /*
         * Validate OAuth state.
         *
         * This protects against CSRF attacks.
         */
        String savedState =
                (String) session.getAttribute(
                        "salesforce_oauth_state"
                );

        if (savedState == null || savedState.isBlank()) {

            Map<String, Object> response = new HashMap<>();

            response.put("success", false);
            response.put(
                    "error",
                    "OAuth state is missing"
            );
            response.put(
                    "message",
                    "The OAuth session may have expired. "
                            + "Please start login again."
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        if (!savedState.equals(state)) {

            System.out.println("OAuth STATE VALIDATION FAILED");
            System.out.println("Expected state: " + savedState);
            System.out.println("Received state: " + state);

            Map<String, Object> response = new HashMap<>();

            response.put("success", false);
            response.put(
                    "error",
                    "Invalid OAuth state"
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        /*
         * Get PKCE verifier saved when /auth/login was called.
         */
        String codeVerifier =
                (String) session.getAttribute(
                        "salesforce_code_verifier"
                );

        if (codeVerifier == null || codeVerifier.isBlank()) {

            Map<String, Object> response = new HashMap<>();

            response.put("success", false);
            response.put(
                    "error",
                    "PKCE code verifier is missing"
            );
            response.put(
                    "message",
                    "The OAuth session is invalid or expired. "
                            + "Please start login again."
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        try {

            /*
             * Exchange authorization code for Salesforce
             * access token.
             */
            System.out.println(
                    "Exchanging authorization code for Salesforce token..."
            );

            JsonNode tokenResponse =
                    salesforceOAuthService.exchangeCodeForToken(
                            code,
                            codeVerifier
                    );

            System.out.println(
                    "Salesforce token response received."
            );

            /*
             * Check whether Salesforce returned an error.
             */
            if (tokenResponse.has("error")) {

                String tokenError =
                        tokenResponse
                                .get("error")
                                .asText();

                String tokenErrorDescription =
                        tokenResponse.has("error_description")
                                ? tokenResponse
                                    .get("error_description")
                                    .asText()
                                : "Salesforce token exchange failed";

                System.out.println(
                        "Salesforce token error: "
                                + tokenError
                );

                System.out.println(
                        "Description: "
                                + tokenErrorDescription
                );

                Map<String, Object> response =
                        new HashMap<>();

                response.put("success", false);
                response.put("error", tokenError);
                response.put(
                        "error_description",
                        tokenErrorDescription
                );

                return ResponseEntity
                        .badRequest()
                        .body(response);
            }

            /*
             * Access token is mandatory.
             */
            if (!tokenResponse.has("access_token")
                    || tokenResponse
                    .get("access_token")
                    .asText()
                    .isBlank()) {

                Map<String, Object> response =
                        new HashMap<>();

                response.put("success", false);
                response.put(
                        "error",
                        "Salesforce access token was not returned"
                );
                response.put(
                        "token_response",
                        tokenResponse
                );

                return ResponseEntity
                        .internalServerError()
                        .body(response);
            }

            /*
             * Save access token in the HTTP session.
             */
            String accessToken =
                    tokenResponse
                            .get("access_token")
                            .asText();

            session.setAttribute(
                    "salesforce_access_token",
                    accessToken
            );

            /*
             * Save Salesforce instance URL.
             *
             */
            if (tokenResponse.has("instance_url")) {

                String instanceUrl =
                        tokenResponse
                                .get("instance_url")
                                .asText();

                session.setAttribute(
                        "salesforce_instance_url",
                        instanceUrl
                );

                System.out.println(
                        "Salesforce Instance URL: "
                                + instanceUrl
                );
            }

            /*
             * Save refresh token if Salesforce returns one.
             */
            if (tokenResponse.has("refresh_token")) {

                session.setAttribute(
                        "salesforce_refresh_token",
                        tokenResponse
                                .get("refresh_token")
                                .asText()
                );

                System.out.println(
                        "Salesforce refresh token received."
                );
            }

            /*
             * Remove temporary OAuth values.
             *
             * They are no longer required after successful
             * token exchange.
             */
            session.removeAttribute(
                    "salesforce_oauth_state"
            );

            session.removeAttribute(
                    "salesforce_code_verifier"
            );

            System.out.println();
            System.out.println(
                    "=========================================="
            );
            System.out.println(
                    "SALESFORCE LOGIN SUCCESSFUL"
            );
            System.out.println(
                    "Session ID: " + session.getId()
            );
            System.out.println(
                    "Access token stored in session."
            );
            System.out.println(
                    "Redirecting to React..."
            );
            System.out.println(
                    "=========================================="
            );
            System.out.println();

            /*
             * Redirect user back to React.
             * This must be http://localhost:5173/
             * and NOT /auth/callback.
             */
            HttpHeaders headers = new HttpHeaders();

            headers.setLocation(
                    URI.create("http://localhost:5173/")
            );

            return ResponseEntity
                    .status(302)
                    .headers(headers)
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", false);
            response.put(
                    "error",
                    "Salesforce OAuth token exchange failed"
            );
            response.put(
                    "message",
                    e.getMessage()
            );

            return ResponseEntity
                    .internalServerError()
                    .body(response);
        }
    }

    /**
     * Checks whether the current browser session
     * is authenticated with Salesforce.
     *
     * GET:
     * http://localhost:8080/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status(
            HttpSession session
    ) {

        String accessToken =
                (String) session.getAttribute(
                        "salesforce_access_token"
                );

        String instanceUrl =
                (String) session.getAttribute(
                        "salesforce_instance_url"
                );

        boolean authenticated =
                accessToken != null
                        && !accessToken.isBlank();

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "authenticated",
                authenticated
        );

        if (authenticated) {

            response.put(
                    "instanceUrl",
                    instanceUrl
            );
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Logs the current user out.
     *
     * GET:
     * http://localhost:8080/auth/logout
     */
    @GetMapping("/logout")
    public ResponseEntity<?> logout(
            HttpSession session
    ) {

        System.out.println(
                "Logging out Salesforce session: "
                        + session.getId()
        );

        session.invalidate();

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "success",
                true
        );

        response.put(
                "message",
                "Logged out successfully"
        );

        return ResponseEntity.ok(response);
    }
}