sessionId: 4d7fa2f6-fb0b-4e2b-ab54-fb694d0e4572
date: '2026-02-24T09:11:24.737Z'
label: Spring Security OAuth2 invalid_request (redirect URI mismatch)
---
Summary for follow-up AI agents — Conversation context and work to continue

Context
- This session began with diagnosing a runtime OAuth2 error logged as:
  - Timestamp: 2026-02-24T09:56:47.920+01:00
  - Exception: org.springframework.security.oauth2.core.OAuth2AuthenticationException: [invalid_request]
  - Stack trace started at OAuth2LoginAuthenticationFilter.attemptAuthentication(...)
- The repository is a Spring Boot app in workspace root; relevant modules are under app/src/main.
- After investigation, the user confirmed the OAuth2 problem is resolved ("now that works") and requested an additional feature: present a "Not Authorized" page when users are not permitted to see a page.

What was changed (applied)
All edits listed below were created/applied in this session.

1) New Thymeleaf template
- Path: app/src/main/resources/templates/not-authorized.html
- Purpose: user-friendly 403 page (Access Denied) integrated with existing theme/navigation.
- Notes: template uses fragment navigation :: mainNav (navigation fragment located at app/src/main/resources/templates/navigation.html in the workspace), displays message from model attribute "message", and optionally shows "requiredRole" if provided.

2) Global exception handler updated
- Path: app/src/main/java/com/govinc/GlobalExceptionHandler.java
- Change: the @ExceptionHandler for com.govinc.authorization.UnauthorizedException now returns the not-authorized view for HTML requests instead of the generic error view.
  - Behavior:
    - If request expects JSON / API: returns JSON { error: "Forbidden", message: ..., status:403 } with HTTP 403.
    - If page request: returns ModelAndView with view name "not-authorized" and HTTP 403.
  - Other behavior retained: generic Exception handler still renders error view and includes stack trace details when dev profile active.
  - addLayoutConfigToView still called to supply layoutConfig model attributes.

3) SecurityConfig updated
- Path: app/src/main/java/com/govinc/configuration/SecurityConfig.java
- Changes:
  - Added /not-authorized to the PUBLIC_URLS whitelist so the not-authorized page can be accessed without authentication/authorization loops.
  - Registered a custom AccessDeniedHandler bean:
    - Bean method: accessDeniedHandler() returns SecurityAccessDeniedHandler (implements org.springframework.security.web.access.AccessDeniedHandler).
    - SecurityFilterChain config: .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler()))
  - The custom AccessDeniedHandler behavior:
    - For API calls (Accept header contains application/json, requestUri contains "/api/", or X-Requested-With == "XMLHttpRequest"): returns JSON { error: "Forbidden", message: "...", status:403 } and sets HTTP 403.
    - For page requests: sets response status 403, sets request attribute "message", and forwards to "/not-authorized" (request.getRequestDispatcher("/not-authorized").forward(...)).
    - Logs access denial attempts with username (if available), request URI and method.
  - Debug OAuth2 filters (OAuth2AuthorizationRequestLoggingFilter and OAuth2DebugLoggingFilter) remain present; oauth2 login config untouched other than AccessDenied integration.

4) New controller to serve the not-authorized view
- Path: app/src/main/java/com/govinc/controller/AccessDeniedController.java
- Endpoint: @GetMapping("/not-authorized")
- Behavior: reads request attribute "message" (set by AccessDeniedHandler or can be set elsewhere), populates model attribute "message", returns view "not-authorized".

Current behavior (what will happen at runtime)
- Controller throws com.govinc.authorization.UnauthorizedException:
  - GlobalExceptionHandler will:
    - For API calls: return HTTP 403 JSON.
    - For page requests: render templates/not-authorized.html with message and HTTP 403.
- Spring Security denies access (AccessDeniedException):
  - SecurityAccessDeniedHandler will:
    - For API calls: return HTTP 403 JSON.
    - For page requests: forward to /not-authorized (now a permitAll URL), where AccessDeniedController will render not-authorized.html.
- The not-authorized page is accessible without authentication because /not-authorized added to PUBLIC_URLS.

Notes, decisions and rationale
- Use of a dedicated not-authorized template (vs re-using error.html) to provide clearer UX and dedicated messaging.
- AccessDeniedHandler forwards to /not-authorized rather than returning a ModelAndView directly; forwarding ensures controller logic (layout model population in AccessDeniedController or GlobalExceptionHandler) can run and navigation fragment can be included.
- API calls are handled separately to preserve JSON error semantics (no HTML pages for APIs).
- /not-authorized was added to PUBLIC_URLS to avoid an auth loop when forwarding.

Pending items / recommended follow-ups (open tasks)
- Provide the "requiredRole" model attribute where relevant:
  - The template supports th:if="${requiredRole != null}" but no code currently sets "requiredRole". Optionally enhance AccessDeniedHandler or controllers to include the specific missing role or permission when denying access.
- Ensure no import/compilation issues:
  - SecurityConfig imports org.springframework.security.web.access.AccessDeniedHandler; verify project compiles and all versions align (Spring Security 6.x used in repository).
- Verify navigation fragment exists and is safe to include on a forwarded request:
  - Navigation fragment referenced as navigation :: mainNav (file app/src/main/resources/templates/navigation.html exists per templates listing).
- Test behavior in these flows:
  - Browser GET of a restricted page → deny → not-authorized page renders with 403 status.
  - Controller throwing UnauthorizedException for API requests → JSON 403 returned.
  - OAuth2 flows and forwarded requests behind proxies: confirm no header/session differences when forwarding to /not-authorized.
- Add unit/integration tests for:
  - AccessDeniedHandler behavior for HTML and API calls.
  - GlobalExceptionHandler UnauthorizedException handling.
  - Not-authorized template rendering with and without "message" attribute.
- Review logging level: access denied events are logged at WARN; confirm this matches your logging/auditing policy.

Files changed (applied)
- app/src/main/resources/templates/not-authorized.html — new file (template).
- app/src/main/java/com/govinc/GlobalExceptionHandler.java — modified UnauthorizedException handler to use not-authorized view (applied).
- app/src/main/java/com/govinc/configuration/SecurityConfig.java — added AccessDeniedHandler bean, registered it in HttpSecurity.exceptionHandling(), added /not-authorized to PUBLIC_URLS (applied).
- app/src/main/java/com/govinc/controller/AccessDeniedController.java — new controller mapping /not-authorized (applied).

Open state of changesets
- All proposed changes listed above were applied in-session. There are no remaining pending diffs from this session.
- No additional file suggestions remain open.

Relevant URIs / file paths (quick reference)
- Not-authorized template: app/src/main/resources/templates/not-authorized.html
- Error template (existing): app/src/main/resources/templates/error.html
- Global exception handling: app/src/main/java/com/govinc/GlobalExceptionHandler.java
- Security config: app/src/main/java/com/govinc/configuration/SecurityConfig.java
- AccessDenied controller: app/src/main/java/com/govinc/controller/AccessDeniedController.java
- Login page: app/src/main/resources/templates/login.html
- OAuth2 dynamic client code: app/src/main/java/com/govinc/configuration/DynamicOAuth2ClientConfiguration.java (context from earlier investigation)
- Auth config endpoints: app/src/main/java/com/govinc/controller/AuthConfigController.java
- Auth service: app/src/main/java/com/govinc/service/AuthConfigService.java

If you want me to continue
- I can:
  - Add requiredRole population in AccessDeniedHandler (extract missing role from security exception or SecurityContext and pass to model).
  - Create unit/integration tests for the new flows.
  - Run a backend compile/check (if CI available) or lint imports to ensure build success.
  - Adjust the not-authorized template copy/messages / localization.
  - Add telemetry/audit event creation for each access-denied event.

Use this summary to resume work or to instruct specialized agents which of the above follow-ups to implement next.