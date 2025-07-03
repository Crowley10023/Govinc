package com.govinc.configuration;

import java.io.*;
import java.util.Properties;

public class IamConfigFileUtil {
    public static void saveToPropertiesFile(IamConfig config, String filename) throws IOException {
        Properties props = new Properties();
        props.setProperty("iam.provider", config.getProvider() == null ? "" : config.getProvider());
        // Azure
        props.setProperty("iam.azure-client-id", config.getAzureClientId() == null ? "" : config.getAzureClientId());
        props.setProperty("iam.azure-client-secret", config.getAzureClientSecret() == null ? "" : config.getAzureClientSecret());
        props.setProperty("iam.azure-tenant-id", config.getAzureTenantId() == null ? "" : config.getAzureTenantId());
        // Keycloak
        props.setProperty("iam.keycloak-issuer-url", config.getKeycloakIssuerUrl() == null ? "" : config.getKeycloakIssuerUrl());
        props.setProperty("iam.keycloak-realm", config.getKeycloakRealm() == null ? "" : config.getKeycloakRealm());
        props.setProperty("iam.keycloak-client-id", config.getKeycloakClientId() == null ? "" : config.getKeycloakClientId());
        props.setProperty("iam.keycloak-client-secret", config.getKeycloakClientSecret() == null ? "" : config.getKeycloakClientSecret());

        // Load existing file to preserve non-iam keys
        Properties existing = new Properties();
        try (InputStream in = new FileInputStream(filename)) {
            existing.load(in);
        }
        // Remove old iam.*
        existing.keySet().removeIf(k -> k.toString().startsWith("iam."));
        // Put all iam.* back
        props.forEach((k, v) -> existing.setProperty((String)k, (String)v));
        try (OutputStream out = new FileOutputStream(filename)) {
            existing.store(out, "Updated IAM settings");
        }
    }
}
