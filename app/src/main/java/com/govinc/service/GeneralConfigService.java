package com.govinc.service;

import com.govinc.entity.GeneralConfig;
import com.govinc.repository.GeneralConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Provides access to general application configuration with an in-memory cache
 * so session-timeout checks do not require a DB query on every HTTP request.
 */
@Service
public class GeneralConfigService {

    @Autowired
    private GeneralConfigRepository generalConfigRepository;

    private volatile int cachedTimeoutMinutes = 30;
    private volatile boolean initialized = false;

    /** Returns the configured session timeout in minutes (cached). */
    public int getSessionTimeoutMinutes() {
        if (!initialized) {
            refresh();
        }
        return cachedTimeoutMinutes;
    }

    /** Forces a reload of the cached config from the database. */
    public void refresh() {
        generalConfigRepository.findByConfigKey("default").ifPresent(c ->
                cachedTimeoutMinutes = c.getSessionTimeoutMinutes());
        initialized = true;
    }

    /** Returns the persisted config row, creating a default one if absent. */
    public GeneralConfig getOrCreate() {
        return generalConfigRepository.findByConfigKey("default").orElseGet(() -> {
            GeneralConfig c = new GeneralConfig();
            c.setConfigKey("default");
            return generalConfigRepository.save(c);
        });
    }
}
