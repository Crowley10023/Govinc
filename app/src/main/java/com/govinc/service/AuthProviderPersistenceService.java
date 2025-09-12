package com.govinc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthProviderPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(AuthProviderPersistenceService.class);
    
    @Value("${auth.persistence.file.path:config/auth-providers.json}")
    private String persistenceFilePath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> persistedProviders = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        loadPersistedProviders();
    }
    
    public void saveProvider(AuthConfigService.AuthProvider provider) {
        try {
            Map<String, Object> providerData = new HashMap<>();
            providerData.put("id", provider.getId());
            providerData.put("displayName", provider.getDisplayName());
            providerData.put("clientId", provider.getClientId());
            providerData.put("clientSecret", provider.getClientSecret());
            providerData.put("issuerUri", provider.getIssuerUri());
            providerData.put("tenantId", provider.getTenantId());
            providerData.put("redirectUri", provider.getRedirectUri());
            providerData.put("type", provider.getType().toString());
            
            persistedProviders.put(provider.getId(), providerData);
            writePersistenceFile();
            
            logger.info("Persisted provider configuration: {}", provider.getId());
            
        } catch (Exception e) {
            logger.error("Failed to persist provider {}: {}", provider.getId(), e.getMessage());
        }
    }
    
    public void removeProvider(String providerId) {
        try {
            persistedProviders.remove(providerId);
            writePersistenceFile();
            
            logger.info("Removed persisted provider configuration: {}", providerId);
            
        } catch (Exception e) {
            logger.error("Failed to remove persisted provider {}: {}", providerId, e.getMessage());
        }
    }
    
    public Map<String, AuthConfigService.AuthProvider> loadPersistedProviders() {
        Map<String, AuthConfigService.AuthProvider> providers = new HashMap<>();
        
        try {
            Path filePath = Paths.get(persistenceFilePath);
            
            if (!Files.exists(filePath)) {
                logger.info("No persistence file found at: {}", persistenceFilePath);
                return providers;
            }
            
            String jsonContent = Files.readString(filePath);
            if (jsonContent.trim().isEmpty()) {
                logger.info("Persistence file is empty: {}", persistenceFilePath);
                return providers;
            }
            
            TypeReference<Map<String, Map<String, Object>>> typeRef = new TypeReference<>() {};
            Map<String, Map<String, Object>> persistedData = objectMapper.readValue(jsonContent, typeRef);
            
            for (var entry : persistedData.entrySet()) {
                try {
                    AuthConfigService.AuthProvider provider = createProviderFromData(entry.getValue());
                    providers.put(entry.getKey(), provider);
                    persistedProviders.put(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.error("Failed to restore provider {}: {}", entry.getKey(), e.getMessage());
                }
            }
            
            logger.info("Loaded {} persisted authentication providers", providers.size());
            
        } catch (Exception e) {
            logger.error("Failed to load persisted providers: {}", e.getMessage());
        }
        
        return providers;
    }
    
    private AuthConfigService.AuthProvider createProviderFromData(Map<String, Object> data) {
        String id = (String) data.get("id");
        String displayName = (String) data.get("displayName");
        String clientId = (String) data.get("clientId");
        String clientSecret = (String) data.get("clientSecret");
        String issuerUri = (String) data.get("issuerUri");
        String tenantId = (String) data.get("tenantId");
        String redirectUri = (String) data.get("redirectUri");
        String typeStr = (String) data.get("type");
        
        AuthConfigService.AuthProviderType type = AuthConfigService.AuthProviderType.valueOf(typeStr);
        
        return new AuthConfigService.AuthProvider(
            id, displayName, clientId, clientSecret, 
            issuerUri, tenantId, redirectUri, type
        );
    }
    
    private void writePersistenceFile() throws IOException {
        Path filePath = Paths.get(persistenceFilePath);
        
        // Create directory if it doesn't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                                       .writeValueAsString(persistedProviders);
        Files.writeString(filePath, jsonContent);
    }
    
    public boolean hasPersistenceFile() {
        return Files.exists(Paths.get(persistenceFilePath));
    }
    
    public String getPersistenceFilePath() {
        return persistenceFilePath;
    }
}