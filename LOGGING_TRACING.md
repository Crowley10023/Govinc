# OAuth2 Azure Login Flow - Comprehensive Logging Trace

This document describes all the logging statements added to trace the Azure SSO/OAuth2 flow from login click to callback.

## Logging Points Added

### 1. **LoginController.java** - `/login` Page Load
**When:** User clicks "Login with Azure" and comes back to `/login` page
**Logs:**
- Session ID creation
- JSESSIONID cookie presence and attributes (secure, httpOnly, sameSite)
- OAuth2 provider availability (Azure and Keycloak status)
- Whether page rendered successfully

**Log pattern:** `[OAUTH2-FLOW]` with info level

### 2. **SecurityConfig.java** - Filter Registration & OAuth2 Config
**When:** Application startup and filter chain initialization
**Logs:**
- OAuth2 login configuration status
- Number of available OAuth2 providers
- Whether form authentication is used as fallback

**Components:**
- **SecurityConfig bean:** Logs provider configuration at startup
- **OAuth2AuthorizationRequestLoggingFilter:** (new) Logs every OAuth2/login request:
  - Session ID
  - OAuth2AuthorizationRequest presence in session
  - State parameter tracking
  - All session attributes related to OAuth2
- **OAuth2DebugLoggingFilter:** Logs all OAuth2-related requests:
  - HTTP method, path, query string
  - Session ID
  - JSESSIONID cookie attributes (path, domain, secure, httpOnly)
  - Request headers (Referer, Host, X-Forwarded-Proto, X-Forwarded-Host)

### 3. **DynamicOAuth2ClientConfiguration.java** - Client Registration
**When:** OAuth2 client registrations are looked up/refreshed
**Logs:**
- `findByRegistrationId()` calls with requested registration ID
- Client registration found/not found status
- Available registrations in repository
- Provider type and configuration status
- Azure registration creation:
  - Tenant ID
  - Client ID
  - Redirect URI
  - Authorization URI
- Any errors during registration creation

**Log pattern:** `[OAUTH2-FLOW]` with info/debug level

### 4. **CustomAuthenticationSuccessHandler.java** - Callback Success
**When:** Azure OAuth2 callback succeeds (user authenticated)
**Logs:**
- Session ID of callback request
- Authentication type (OidcUser, UserDetails, or String)
- Principal class name
- Request URL and method
- Request origin (remote address)
- JSESSIONID cookie presence
- OidcUser claim resolution:
  - preferred_username (Keycloak)
  - email fallback
  - sub claim (Azure)
- Database user lookup and creation
- Final redirect to `/`

**Log pattern:** `[OAUTH2-FLOW]` with info/debug level

## Key Data Points Captured

### Session/Cookie Information
- **JSESSIONID:** Presence, value, secure flag, httpOnly flag, path, domain
- **Session ID:** Before and after OAuth provider redirect

### OAuth2 State Parameter
- **Authorization Request:** Stored in session before redirect
- **State:** Retrieved during callback
- **Attributes:** All session attributes containing "AUTHORIZATION" or "oauth"

### Request Headers
- **Referer:** Where the request came from
- **Host:** Request host
- **X-Forwarded-Proto:** Protocol (http/https) from proxy
- **X-Forwarded-Host:** Host from proxy

### Authentication Details
- **Principal:** Type and value
- **Claims:** All OIDC claims from Azure
- **User Resolution:** Username extraction process

## Log Filtering

All logs are prefixed with `[OAUTH2-FLOW]` for easy filtering:

```bash
# View only OAuth2 flow logs
grep "\[OAUTH2-FLOW\]" application.log

# View by component
grep "\[OAUTH2-FLOW\] \[FILTER\]" application.log  # Filter logs

# View by severity
grep "\[OAUTH2-FLOW\].*ERROR" application.log
```

## Expected Log Sequence

```
1. [OAUTH2-FLOW] GET /login - Session ID created
2. [OAUTH2-FLOW] JSESSIONID cookie found
3. [OAUTH2-FLOW] Login page OAuth2 provider availability: Azure=true
4. [OAUTH2-FLOW] GET /oauth2/authorization/azure - Click login button
5. [OAUTH2-FLOW] [FILTER] OAuth2AuthorizationRequest created and stored in session
6. [OAUTH2-FLOW] [FILTER] State parameter: xxxxx
7. [OAUTH2-FLOW] User redirected to Azure login
8. [OAUTH2-FLOW] GET /login/oauth2/code/azure?code=... - Callback from Azure
9. [OAUTH2-FLOW] [FILTER] OAuth2AuthorizationRequest found in session
10. [OAUTH2-FLOW] findByRegistrationId("azure")
11. [OAUTH2-FLOW] Found client registration for: azure
12. [OAUTH2-FLOW] onAuthenticationSuccess triggered
13. [OAUTH2-FLOW] OidcUser detected
14. [OAUTH2-FLOW] Resolved username and email
15. [OAUTH2-FLOW] User created/found in database
16. [OAUTH2-FLOW] Redirecting to /
```

## Configuration Changes

**application.properties:**
```properties
# Logging levels set to DEBUG for full tracing
logging.level.com.govinc.configuration=DEBUG
logging.level.com.govinc.service.AuthConfigService=DEBUG
logging.level.com.govinc.controller.LoginController=DEBUG
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.oauth2=DEBUG
logging.level.org.springframework.security.web=DEBUG
logging.level.org.springframework.boot.autoconfigure.security=DEBUG
```

## Troubleshooting Guide

### Problem: OAuth2AuthorizationRequest not found at callback
**Check logs for:**
1. Is authorization request stored in step 5?
2. Is JSESSIONID cookie sent with callback request in step 8?
3. Are session IDs the same in step 1 and step 8?

### Problem: Client registration not found
**Check logs for:**
1. Is Azure provider configured (step 2)?
2. Is `findByRegistrationId("azure")` called (step 10)?
3. What error is logged during registration creation?

### Problem: User attributes not extracted
**Check logs for:**
1. Is OidcUser detected (step 12)?
2. What claims are present in the user token?
3. Which fallback is used (preferred_username, email, sub)?

### Problem: Session lost between requests
**Check logs for:**
1. Session IDs in step 1 and step 8 - are they the same?
2. JSESSIONID cookie attributes - is secure flag causing issue?
3. Proxy headers - are X-Forwarded-Proto and X-Forwarded-Host correct?
4. Browser cookies - verify cookie is sent in browser requests

## Files Modified

- `app/src/main/java/com/govinc/configuration/SecurityConfig.java` - Added filters and logging
- `app/src/main/java/com/govinc/configuration/OAuth2AuthorizationRequestLoggingFilter.java` - New filter for state tracking
- `app/src/main/java/com/govinc/configuration/DynamicOAuth2ClientConfiguration.java` - Enhanced logging
- `app/src/main/java/com/govinc/controller/LoginController.java` - Login page logging
- `app/src/main/java/com/govinc/configuration/CustomAuthenticationSuccessHandler.java` - Callback logging
- `app/src/main/resources/application.properties` - Debug logging enabled
