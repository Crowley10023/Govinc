package com.govinc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class Theia01Application {
    private static final Logger logger = LoggerFactory.getLogger(Theia01Application.class);

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:NOT_SET}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:NOT_SET}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.keycloak.authorization-grant-type:NOT_SET}")
    private String grantType;

    @Value("${spring.security.oauth2.client.registration.keycloak.scope:NOT_SET}")
    private String scope;

    @Value("${spring.security.oauth2.client.registration.keycloak.redirect-uri:NOT_SET}")
    private String redirectUri;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:NOT_SET}")
    private String issuerUri;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("\n========== OAuth2 Client Configuration ==========");
        logger.info("Client ID: {}", clientId);
        logger.info("Client Secret: {}", mask(clientSecret));
        logger.info("Authorization Grant Type: {}", grantType);
        logger.info("Scope: {}", scope);
        logger.info("Redirect URI: {}", redirectUri);
        logger.info("Issuer URI: {}", issuerUri);
        logger.info("================================================\n");
    }

    private String mask(String value) {
        if (value == null || value.equals("NOT_SET")) return value;
        // Mask all but first and last char, unless very short
        int length = value.length();
        if (length <= 2) return "***";
        return value.charAt(0) + "***" + value.charAt(length - 1);
    }

    public static void main(String[] args) {
        SpringApplication.run(Theia01Application.class, args);
    }
}