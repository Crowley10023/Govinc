package com.govinc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared utility for building JSON response strings used by controllers
 * that return raw JSON via @ResponseBody with String return type.
 */
public final class JsonResponseUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonResponseUtil() {}

    public static String buildSuccessResponse(String message) {
        try {
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", message);
            return MAPPER.writeValueAsString(r);
        } catch (Exception e) {
            return "{\"success\":true}";
        }
    }

    public static String buildErrorResponse(String title, String message) {
        try {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("title", title);
            r.put("message", message);
            return MAPPER.writeValueAsString(r);
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }
}
