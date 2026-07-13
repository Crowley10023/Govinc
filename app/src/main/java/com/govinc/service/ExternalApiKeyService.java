package com.govinc.service;

import com.govinc.entity.ExternalApiKey;
import com.govinc.repository.ExternalApiKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ExternalApiKeyService {
    private static final String KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_LENGTH = 40;

    @Autowired
    private ExternalApiKeyRepository externalApiKeyRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public static class CreatedApiKey {
        private final ExternalApiKey stored;
        private final String rawApiKey;

        public CreatedApiKey(ExternalApiKey stored, String rawApiKey) {
            this.stored = stored;
            this.rawApiKey = rawApiKey;
        }

        public ExternalApiKey getStored() {
            return stored;
        }

        public String getRawApiKey() {
            return rawApiKey;
        }
    }

    public List<ExternalApiKey> listAll() {
        return externalApiKeyRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public CreatedApiKey createApiKey(String requestedName) {
        String name = normalizeName(requestedName);
        String raw = generateRawKey();
        String hash = sha256(raw);

        ExternalApiKey key = new ExternalApiKey();
        key.setName(name);
        key.setKeyPrefix(raw.substring(0, Math.min(12, raw.length())));
        key.setKeyHash(hash);
        key.setActive(true);
        key.setCreatedAt(LocalDateTime.now());

        ExternalApiKey saved = externalApiKeyRepository.save(key);
        return new CreatedApiKey(saved, raw);
    }

    @Transactional
    public boolean revoke(Long id) {
        Optional<ExternalApiKey> optional = externalApiKeyRepository.findById(id);
        if (optional.isEmpty()) {
            return false;
        }
        ExternalApiKey key = optional.get();
        key.setActive(false);
        externalApiKeyRepository.save(key);
        return true;
    }

    @Transactional
    public boolean delete(Long id) {
        if (!externalApiKeyRepository.existsById(id)) {
            return false;
        }
        externalApiKeyRepository.deleteById(id);
        return true;
    }

    @Transactional
    public boolean validateAndTouch(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.trim().isEmpty()) {
            return false;
        }

        String hash = sha256(rawApiKey.trim());
        Optional<ExternalApiKey> optional = externalApiKeyRepository.findByKeyHashAndActiveTrue(hash);
        if (optional.isEmpty()) {
            return false;
        }

        ExternalApiKey key = optional.get();
        key.setLastUsedAt(LocalDateTime.now());
        externalApiKeyRepository.save(key);
        return true;
    }

    private String normalizeName(String requestedName) {
        if (requestedName == null || requestedName.trim().isEmpty()) {
            return "External Integration Key";
        }
        String trimmed = requestedName.trim();
        if (trimmed.length() > 120) {
            return trimmed.substring(0, 120);
        }
        return trimmed;
    }

    private String generateRawKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH + 4);
        sb.append("gk_");
        for (int i = 0; i < KEY_LENGTH; i++) {
            int idx = secureRandom.nextInt(KEY_CHARS.length());
            sb.append(KEY_CHARS.charAt(idx));
        }
        return sb.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
