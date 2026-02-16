# Azure Secret Validation Error Handling

## Overview

Added comprehensive error handling for Azure OAuth2 authentication failures, specifically for invalid or expired client secrets. When authentication fails, users now receive helpful error messages on the login page.

## What Changed

### 1. SecurityConfig.java - Custom Failure Handler

**New Component:** `OAuth2ErrorHandlingFailureHandler`

This custom failure handler:
- Catches OAuth2 authentication exceptions
- Detects specific error types:
  - `invalid_client` / `Client authentication failed` → **Invalid Secret Error**
  - `invalid_grant` → **Authorization Code Error**
  - `unauthorized_client` → **Application Not Authorized Error**
  - Other errors → **Generic OAuth2 Error**
- Stores user-friendly error messages in the session
- Redirects to `/login?error=<error_type>` with the specific error type

**Error Detection Logic:**
```java
if (exceptionMessage.contains("invalid_client") || exceptionMessage.contains("Client authentication failed"))
    → errorMessage = "invalid_secret"
    → errorDetails = "The OAuth2 client secret is invalid or expired..."
```

### 2. LoginController.java - Enhanced Login Page Data

**New Logic:**
- Checks for OAuth2 error parameters in the request
- Maps error codes to user-friendly messages:
  - `invalid_secret` → "Azure Configuration Error"
  - `invalid_grant` → "Authorization Error"
  - `unauthorized_client` → "Application Not Authorized"
  - `oauth2_error` → "OAuth2 Authentication Failed"
- Retrieves detailed error messages from session
- Passes error data to the login template

**Error Messages Map:**
| Error Code | Title | Message |
|---|---|---|
| `invalid_secret` | Azure Configuration Error | The OAuth2 client secret is invalid or expired. Please verify your Azure app registration credentials and update them in the configuration. |
| `invalid_grant` | Authorization Error | The authorization code expired or is invalid. Please try logging in again. |
| `unauthorized_client` | Application Not Authorized | The application is not authorized in Azure. Please check the app registration settings. |
| `oauth2_error` | OAuth2 Authentication Failed | An authentication error occurred. Please check the Azure configuration and try again. |

### 3. login.html - Error Display UI

**New Elements:**
- `.oauth2-error-box` - Styled error message container
  - Red background (#fde7e7)
  - Red border (#d63c3c)
  - Title showing error type
  - Detailed error message
  - Error code reference

**Display Logic:**
- Shows OAuth2 error box if `errorMessage` variable is set
- Shows form error message if regular form login fails
- Both error displays can coexist without conflict

## How It Works

### Flow Diagram

```
User clicks "Login with Azure"
    ↓
Azure login flow
    ↓
Authorization code returned
    ↓
Token exchange fails (invalid secret)
    ↓
OAuth2ErrorHandlingFailureHandler.onAuthenticationFailure() called
    ↓
Exception parsed for specific error type
    ↓
Error details stored in session
    ↓
Redirect to /login?error=invalid_secret
    ↓
LoginController.login() processes error parameter
    ↓
Error message retrieved from session
    ↓
login.html renders with error box displayed
```

## Usage

### For Users
1. Click "Login with Azure"
2. If the secret is invalid:
   - User is redirected to login page
   - Error box displays at top: **"Azure Configuration Error"**
   - Message explains: **"The OAuth2 client secret is invalid or expired. Please verify your Azure app registration credentials..."**

### For Administrators
When an invalid secret error occurs:
1. Check the error message on the login page
2. Log files will contain: `[OAUTH2-FLOW] Detected invalid client secret error`
3. Update Azure configuration:
   - Navigate to `/admin/auth-config`
   - Verify Client ID and Secret in Azure portal
   - Update both fields with current values
   - Click "Save Configuration"

## Logging

All error detection is logged with `[OAUTH2-FLOW]` prefix at WARN level:
```
[OAUTH2-FLOW] Detected invalid client secret error
[OAUTH2-FLOW] Stored error details in session: The OAuth2 client secret is invalid...
```

## Error Codes

### Invalid Secret (invalid_secret)
- **Cause:** OAuth2 client secret is incorrect or expired in Azure app registration
- **User Action:** Contact administrator to update credentials
- **Admin Action:** Update Azure app registration secret

### Invalid Grant (invalid_grant)
- **Cause:** Authorization code is invalid or has expired (typically after 10 minutes)
- **User Action:** Try logging in again
- **Admin Action:** Usually no action needed, user should retry

### Unauthorized Client (unauthorized_client)
- **Cause:** Application is not authorized in Azure, redirect URI mismatch, or app registration issues
- **User Action:** Contact administrator
- **Admin Action:** Verify app registration in Azure:
  - Check Client ID matches
  - Verify redirect URI includes: `https://[your-domain]/login/oauth2/code/azure`
  - Check app permissions in Azure

## Files Modified

- `app/src/main/java/com/govinc/configuration/SecurityConfig.java` - Added OAuth2ErrorHandlingFailureHandler
- `app/src/main/java/com/govinc/controller/LoginController.java` - Enhanced error handling and display logic
- `app/src/main/resources/templates/login.html` - Added OAuth2 error box styling and display

## Testing

### Test Invalid Secret Scenario
1. Go to `/admin/auth-config`
2. Edit Azure configuration
3. Change Client Secret to an invalid value
4. Click "Save Configuration"
5. Try to login with Azure
6. Should see: **"Azure Configuration Error"** message

### Test Valid Secret Scenario
1. Restore the correct Client Secret
2. Click "Save Configuration"
3. Try to login with Azure
4. Should proceed normally (or show different error if other issues)

## Future Enhancements

Possible improvements:
- Add direct link to admin config from error message
- Auto-detect and retry on temporary errors
- Track failed attempts and alert admin
- Add email notification on configuration errors
- Implement PKCE flow for better security
