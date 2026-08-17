package com.salesforce.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SalesforceCrudBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SalesforceCrudBackendApplication.class,
                args
        );
    }
}