package com.salesforce.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.backend.util.PkceUtil;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class SalesforceOAuthService {

    private static final String CODE_VERIFIER =
            "salesforce_code_verifier";

    private static final String OAUTH_STATE =
            "salesforce_oauth_state";

    @Value("${salesforce.login-url}")
    private String loginUrl;

    @Value("${salesforce.client-id}")
    private String clientId;

    @Value("${salesforce.client-secret}")
    private String clientSecret;

    @Value("${salesforce.callback-url}")
    private String callbackUrl;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public SalesforceOAuthService() {

        this.restClient =
                RestClient.builder().build();

        this.objectMapper =
                new ObjectMapper();
    }

    /*
    | Create Salesforce Authorization URL
    */

    public String createAuthorizationUrl(
            HttpSession session
    ) {

        String codeVerifier =
                PkceUtil.generateCodeVerifier();

        String codeChallenge =
                PkceUtil.generateCodeChallenge(
                        codeVerifier
                );

        String state =
                PkceUtil.generateState();

        /*
         * Store PKCE verifier in HTTP session.
         */
        session.setAttribute(
                CODE_VERIFIER,
                codeVerifier
        );

        /*
         * Store OAuth state in HTTP session.
         */
        session.setAttribute(
                OAUTH_STATE,
                state
        );

        String authorizationUrl =
                loginUrl
                        + "/services/oauth2/authorize"
                        + "?response_type=code"
                        + "&client_id="
                        + encode(clientId)
                        + "&redirect_uri="
                        + encode(callbackUrl)
                        + "&state="
                        + encode(state)
                        + "&code_challenge="
                        + encode(codeChallenge)
                        + "&code_challenge_method=S256"
                        + "&scope="
                        + encode(
                        "api refresh_token offline_access"
                );

        return authorizationUrl;
    }

    /*
    | Exchange Authorization Code For Access Token
    */

    public JsonNode exchangeCodeForToken(
            String code,
            String codeVerifier
    ) {

        String tokenUrl =
                loginUrl
                        + "/services/oauth2/token";

        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add(
                "grant_type",
                "authorization_code"
        );

        form.add(
                "code",
                code
        );

        form.add(
                "client_id",
                clientId
        );

        /*
         * Salesforce external client apps can be configured
         * as confidential or public clients.
         *
         * If a client secret is configured, send it.
         */
        if (
                clientSecret != null
                        && !clientSecret.isBlank()
        ) {

            form.add(
                    "client_secret",
                    clientSecret
            );
        }

        form.add(
                "redirect_uri",
                callbackUrl
        );

        form.add(
                "code_verifier",
                codeVerifier
        );

        try {

            String responseBody =
                    restClient
                            .post()
                            .uri(tokenUrl)
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .body(form)
                            .retrieve()
                            .body(String.class);

            if (
                    responseBody == null
                            || responseBody.isBlank()
            ) {

                throw new IllegalStateException(
                        "Salesforce returned an empty token response"
                );
            }

            JsonNode response =
                    objectMapper.readTree(
                            responseBody
                    );

            /*
             * Salesforce may return:
             *
             * {
             *   "error": "...",
             *   "error_description": "..."
             * }
             */

            if (response.has("error")) {

                String error =
                        response
                                .path("error")
                                .asText();

                String description =
                        response
                                .path("error_description")
                                .asText();

                throw new IllegalStateException(
                        "Salesforce OAuth error: "
                                + error
                                + " - "
                                + description
                );
            }

            return response;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to exchange Salesforce authorization code: "
                            + e.getMessage(),
                    e
            );
        }
    }

    /*
    | URL Encode
    */

    private String encode(
            String value
    ) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}