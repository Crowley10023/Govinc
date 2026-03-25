package com.govinc.user;

import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.service.AuthConfigService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/users/azure")
public class AzureUserImportController {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AuthConfigService authConfigService;

    @Autowired
    private UserRepository userRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    // ── Search ────────────────────────────────────────────────────────────────

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, String> body) {
        if (!authorizationService.isAdmin()) throw new UnauthorizedException("Admin required");

        AuthConfigService.AuthProvider azure = authConfigService.getProvider("azure");
        if (azure == null || !azure.isConfigured()) {
            return ResponseEntity.ok(Map.of("error", "Azure AD is not configured. Please set client ID, secret and tenant ID under Admin → Auth Config."));
        }

        String query = body.getOrDefault("query", "").trim();
        if (query.isBlank()) {
            return ResponseEntity.ok(Map.of("error", "Search query must not be empty."));
        }

        try {
            String token = acquireAppToken(azure);
            List<Map<String, String>> results = searchGraphUsers(token, query);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", "Graph API error: " + e.getMessage()));
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importUsers(@RequestBody List<Map<String, String>> users) {
        if (!authorizationService.isAdmin()) throw new UnauthorizedException("Admin required");

        int imported = 0;
        int skipped = 0;
        List<String> skippedEmails = new ArrayList<>();

        for (Map<String, String> u : users) {
            String email = u.getOrDefault("mail", "").trim().toLowerCase();
            if (email.isBlank()) {
                // Fall back to userPrincipalName if no mail
                email = u.getOrDefault("userPrincipalName", "").trim().toLowerCase();
            }
            if (email.isBlank()) { skipped++; continue; }

            if (userRepository.findByEmail(email).isPresent()) {
                skipped++;
                skippedEmails.add(email);
                continue;
            }

            String givenName = u.getOrDefault("givenName", "");
            String surname   = u.getOrDefault("surname",   "");
            // Fall back: split displayName when individual name parts are missing
            if (givenName.isBlank() && surname.isBlank()) {
                String display = u.getOrDefault("displayName", "");
                int sp = display.lastIndexOf(' ');
                if (sp > 0) {
                    givenName = display.substring(0, sp);
                    surname   = display.substring(sp + 1);
                } else {
                    givenName = display;
                }
            }

            User user = new User(givenName, surname, email);
            user.setRole(Role.ASSESSOR);
            userRepository.save(user);
            imported++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        if (!skippedEmails.isEmpty()) {
            result.put("skippedEmails", skippedEmails);
        }
        return ResponseEntity.ok(result);
    }

    // ── Resolve-or-create (used by email recipient picker) ───────────────────
    /**
     * Given a list of Azure AD user maps (as returned by /search), ensures every
     * user exists in the local DB (creates with ASSESSOR role if not) and returns
     * the DB id, display name and email for each — ready for the email send flow.
     */
    @PostMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolveUsers(@RequestBody List<Map<String, String>> users) {
        if (!authorizationService.isAdmin()) throw new UnauthorizedException("Admin required");

        List<Map<String, Object>> resolved = new ArrayList<>();

        for (Map<String, String> u : users) {
            String emailRaw = u.getOrDefault("mail", "").trim().toLowerCase();
            if (emailRaw.isBlank()) emailRaw = u.getOrDefault("userPrincipalName", "").trim().toLowerCase();
            if (emailRaw.isBlank()) continue;
            final String email = emailRaw;

            String gn = u.getOrDefault("givenName", "");
            String sn = u.getOrDefault("surname",   "");
            if (gn.isBlank() && sn.isBlank()) {
                String display = u.getOrDefault("displayName", "");
                int sp = display.lastIndexOf(' ');
                if (sp > 0) { gn = display.substring(0, sp); sn = display.substring(sp + 1); }
                else         { gn = display; }
            }
            final String givenName = gn;
            final String surname   = sn;

            User dbUser = userRepository.findByEmail(email).orElseGet(() -> {
                User nu = new User(givenName, surname, email);
                nu.setRole(Role.ASSESSOR);
                return userRepository.save(nu);
            });

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",   dbUser.getId());
            entry.put("name", dbUser.getName());
            entry.put("email", dbUser.getEmail());
            resolved.add(entry);
        }

        return ResponseEntity.ok(Map.of("resolved", resolved));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Obtains an access token for https://graph.microsoft.com via the client-credentials flow. */
    private String acquireAppToken(AuthConfigService.AuthProvider azure)
            throws IOException, InterruptedException {
        String body = "grant_type=client_credentials"
                + "&client_id="     + enc(azure.getClientId())
                + "&client_secret=" + enc(azure.getClientSecret())
                + "&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/" + azure.getTenantId() + "/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Token endpoint returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return new JSONObject(resp.body()).getString("access_token");
    }

    /** Searches Azure AD users via the Graph API $search parameter. */
    private List<Map<String, String>> searchGraphUsers(String token, String query)
            throws IOException, InterruptedException {
        // $search supports searching across displayName, mail, userPrincipalName
        String filter = URLEncoder.encode("\"displayName:" + query + "\"", StandardCharsets.UTF_8);
        String url = "https://graph.microsoft.com/v1.0/users"
                + "?$search=" + filter
                + "&$top=25"
                + "&$select=id,displayName,givenName,surname,mail,userPrincipalName"
                + "&$orderby=displayName";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("ConsistencyLevel", "eventual")
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Graph API returned HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JSONArray items = new JSONObject(resp.body()).optJSONArray("value");
        List<Map<String, String>> results = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                Map<String, String> u = new LinkedHashMap<>();
                u.put("id",                o.optString("id",                ""));
                u.put("displayName",       o.optString("displayName",       ""));
                u.put("givenName",         o.optString("givenName",         ""));
                u.put("surname",           o.optString("surname",           ""));
                u.put("mail",              o.optString("mail",              ""));
                u.put("userPrincipalName", o.optString("userPrincipalName", ""));
                results.add(u);
            }
        }
        return results;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
