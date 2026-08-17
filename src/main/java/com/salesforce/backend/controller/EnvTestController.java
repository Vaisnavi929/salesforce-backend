package com.salesforce.backend.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnvTestController {

    @Value("${salesforce.client-id}")
    private String clientId;

    @GetMapping("/test/env")
    public String testEnvironment() {

        if (clientId == null || clientId.isBlank()) {
            return "SALESFORCE_CLIENT_ID is NOT loaded";
        }

        return "SALESFORCE_CLIENT_ID is loaded successfully";
    }
}