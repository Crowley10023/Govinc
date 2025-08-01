package com.govinc.controller;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class AuthProviderAdvice {
    private final Environment env;

    public AuthProviderAdvice(Environment env) {
        this.env = env;
    }

    @ModelAttribute("oauthProviders")
    public Map<String, Boolean> listProviders() {
        // Add more providers to this array as needed
        String[] providers = {"keycloak", "azure", "google", "github"};
        Map<String, Boolean> map = new HashMap<>();
        for (String provider : providers) {
            String cid = env.getProperty(
                    "spring.security.oauth2.client.registration." + provider + ".client-id");
            map.put(provider, cid != null && !cid.isBlank());
        }
        return map;
    }
}
