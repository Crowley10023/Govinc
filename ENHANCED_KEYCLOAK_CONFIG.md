# Enhanced Keycloak Configuration

This document describes the enhancements made to provide detailed health check information and dynamic redirect-URI configuration for Keycloak and other OAuth2 providers.

## New Features

### 1. Detailed Health Check Information

The authentication system now provides comprehensive health check information with specific error codes and detailed explanations for why a provider might be unhealthy.

#### Health Check Error Codes

**Keycloak Provider:**
- `SUCCESS`: Provider is accessible and responding correctly
- `CONFIGURATION_MISSING`: Required configuration parameters are missing
- `CONNECTION_REFUSED`: Cannot connect to Keycloak server (server may be down)
- `CONNECTION_TIMEOUT`: Connection timeout after 10 seconds
- `UNKNOWN_HOST`: DNS resolution failed for Keycloak server
- `HTTP_CLIENT_ERROR_4xx`: HTTP client errors (400-499)
- `HTTP_SERVER_ERROR_5xx`: HTTP server errors (500-599)
- `INVALID_RESPONSE`: Server responded but with invalid OpenID configuration
- `UNEXPECTED_ERROR`: Unexpected error during health check

**Azure AD Provider:**
- `SUCCESS`: Provider is accessible and responding correctly
- `CONFIGURATION_MISSING`: Required configuration parameters are missing
- `CONNECTION_REFUSED`: Cannot connect to Azure AD
- `CONNECTION_TIMEOUT`: Connection timeout after 10 seconds
- `UNKNOWN_HOST`: DNS resolution failed for Azure AD
- `HTTP_CLIENT_ERROR_400`: Invalid tenant ID
- `HTTP_CLIENT_ERROR_404`: Tenant not found
- `HTTP_CLIENT_ERROR_4xx`: Other HTTP client errors
- `HTTP_SERVER_ERROR_5xx`: HTTP server errors
- `INVALID_RESPONSE`: Server responded but with invalid OpenID configuration
- `UNEXPECTED_ERROR`: Unexpected error during health check

### 2. Dynamic Redirect-URI Configuration

Redirect URIs can now be configured and updated through the web interface without requiring application restarts for basic functionality.

#### Supported Redirect-URI Patterns

**Keycloak:**
```
http://your-app-server:8080/login/oauth2/code/keycloak
https://your-app-server:8443/login/oauth2/code/keycloak
```

**Azure AD:**
```
{baseUrl}/login/oauth2/code/{registrationId}
http://your-app-server:8080/login/oauth2/code/azure
https://your-app-server:8443/login/oauth2/code/azure
```

### 3. Enhanced Web Interface

The authentication configuration web interface (`/admin/auth-config`) now includes:

- **Clickable status indicators**: Click on "Healthy" or "Unhealthy" status to view detailed information
- **Real-time health details**: View error codes, messages, and timestamps
- **Configuration preview**: See current client IDs, redirect URIs, and other settings (sensitive data masked)
- **Test connection buttons**: Manually trigger health checks for individual providers
- **Redirect-URI fields**: Configure redirect URIs directly in the web interface
- **Enhanced error feedback**: Detailed error messages for configuration failures

## API Endpoints

### New Endpoints

#### Get Provider Health Details
```http
GET /admin/auth-config/health/{providerId}
```

**Response:**
```json
{
  "providerId": "keycloak",
  "healthy": false,
  "errorCode": "CONNECTION_REFUSED",
  "message": "Connection refused - Keycloak server is not reachable",
  "details": "Cannot connect to http://keycloak:8080/realms/your-realm. Server may be down or network issues exist.",
  "timestamp": "2024-01-15 14:30:25"
}
```

#### Update Redirect-URI
```http
PUT /admin/auth-config/provider/{providerId}/redirect-uri
Content-Type: application/x-www-form-urlencoded

redirectUri=http://localhost:8080/login/oauth2/code/keycloak
```

**Response:**
```json
{
  "status": "success",
  "message": "Redirect URI updated successfully for keycloak. Application restart may be required for full integration."
}
```

### Enhanced Endpoints

#### Get Status (Enhanced)
```http
GET /admin/auth-config/status
```

**Enhanced Response:**
```json
{
  "providers": {
    "keycloak": {
      "displayName": "Keycloak",
      "type": "KEYCLOAK",
      "configured": true,
      "healthy": false,
      "clientId": "Go****nc",
      "redirectUri": "http://localhost:8080/login/oauth2/code/keycloak",
      "issuerUri": "http://keycloak:8080/realms/your-realm",
      "healthCheck": {
        "errorCode": "CONNECTION_REFUSED",
        "message": "Connection refused - Keycloak server is not reachable",
        "details": "Cannot connect to http://keycloak:8080/realms/your-realm. Server may be down or network issues exist.",
        "timestamp": "2024-01-15 14:30:25"
      }
    }
  },
  "hasOAuth2": true
}
```

#### Health Check (Enhanced)
```http
POST /admin/auth-config/health-check
```

**Enhanced Response:**
```json
{
  "status": "success",
  "message": "Health check completed",
  "results": {
    "keycloak": {
      "healthy": false,
      "errorCode": "CONNECTION_REFUSED",
      "message": "Connection refused - Keycloak server is not reachable",
      "details": "Cannot connect to http://keycloak:8080/realms/your-realm. Server may be down or network issues exist.",
      "timestamp": "2024-01-15 14:30:25"
    }
  }
}
```

#### Configure Provider (Enhanced)
```http
POST /admin/auth-config/keycloak
Content-Type: application/x-www-form-urlencoded

clientId=your-client-id&clientSecret=your-client-secret&issuerUri=http://keycloak:8080/realms/your-realm&redirectUri=http://localhost:8080/login/oauth2/code/keycloak
```

## Configuration Examples

### Application Properties

```properties
# Keycloak with redirect-URI
spring.security.oauth2.client.registration.keycloak.client-id=your-client-id
spring.security.oauth2.client.registration.keycloak.client-secret=your-client-secret
spring.security.oauth2.client.registration.keycloak.redirect-uri=http://localhost:8080/login/oauth2/code/keycloak
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://keycloak:8080/realms/your-realm

# Azure AD with redirect-URI
spring.security.oauth2.client.registration.azure.client-id=your-azure-client-id
spring.security.oauth2.client.registration.azure.client-secret=your-azure-client-secret
spring.security.oauth2.client.registration.azure.redirect-uri=http://localhost:8080/login/oauth2/code/azure
spring.security.oauth2.client.provider.azure.tenant-id=your-tenant-id
```

### Web Interface Configuration

1. **Access the configuration interface:**
   ```
   http://your-app:8080/admin/auth-config
   ```

2. **Configure Keycloak:**
   - Client ID: `your-client-id`
   - Client Secret: `your-client-secret`
   - Issuer URI: `http://keycloak:8080/realms/your-realm`
   - Redirect URI: `http://localhost:8080/login/oauth2/code/keycloak`

3. **Test Connection:**
   - Click "Test Connection" to verify configuration
   - View detailed results by clicking on the status indicator

4. **Update Redirect-URI:**
   - Modify the redirect URI field
   - Click "Configure" to apply changes

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. Connection Refused (Keycloak)
**Error Code:** `CONNECTION_REFUSED`  
**Cause:** Keycloak server is not running or not accessible  
**Solutions:**
- Verify Keycloak server is running
- Check network connectivity
- Verify issuer URI is correct
- Check firewall settings

#### 2. Invalid Tenant ID (Azure)
**Error Code:** `HTTP_CLIENT_ERROR_400`  
**Cause:** Azure tenant ID is incorrect  
**Solutions:**
- Verify tenant ID in Azure portal
- Ensure tenant ID format is correct (GUID)
- Check Azure AD configuration

#### 3. Redirect URI Mismatch
**Error Code:** `HTTP_CLIENT_ERROR_400` (during OAuth flow)  
**Cause:** Redirect URI not registered in provider  
**Solutions:**
- Register redirect URI in Keycloak client settings
- Register redirect URI in Azure app registration
- Ensure redirect URI matches exactly (including protocol and port)

#### 4. DNS Resolution Failed
**Error Code:** `UNKNOWN_HOST`  
**Cause:** Cannot resolve provider hostname  
**Solutions:**
- Check DNS settings
- Verify hostname/domain is correct
- Test name resolution from server

### Monitoring and Maintenance

#### Regular Health Checks
The system automatically performs health checks every time the application starts and when providers are configured. You can also:

1. **Manual Health Checks:**
   - Use the "Test Connection" button in the web interface
   - Call the health check API endpoint

2. **Monitor Logs:**
   ```
   INFO  - Authentication provider keycloak is healthy
   WARN  - Authentication provider keycloak is not healthy: Connection refused - Keycloak server is not reachable (Code: CONNECTION_REFUSED, Details: Cannot connect to http://keycloak:8080/realms/your-realm. Server may be down or network issues exist.)
   ```

3. **Status Dashboard:**
   - Access `/admin/auth-config` for real-time status
   - Click status indicators for detailed information

#### Best Practices

1. **Regular Testing:**
   - Test provider connections after configuration changes
   - Monitor health check results
   - Verify redirect URIs are working

2. **Environment-Specific Configuration:**
   - Use different redirect URIs for dev/staging/production
   - Configure appropriate timeouts for network conditions
   - Use HTTPS in production environments

3. **Security Considerations:**
   - Rotate client secrets regularly
   - Use secure protocols (HTTPS) for production
   - Monitor for authentication failures

## Migration from Previous Version

### Automatic Migration
- Existing configurations continue to work without changes
- New detailed health check information is automatically available
- Redirect URIs from properties are preserved

### New Features Available
- Access enhanced web interface at `/admin/auth-config`
- Click on status indicators to view detailed health information
- Configure redirect URIs through the web interface
- Use new API endpoints for integration

### Backward Compatibility
All existing functionality remains unchanged:
- Property-based configuration still works
- Existing API endpoints maintain same behavior
- Authentication flow remains the same

## Additional Resources

### Related Files
- `AuthProviderHealthService.java` - Enhanced health checking logic
- `AuthConfigService.java` - Provider management with redirect-URI support  
- `AuthConfigController.java` - Enhanced REST API endpoints
- `auth-config.html` - Enhanced web interface

### Configuration Reference
- See `application.properties` for complete configuration examples
- Refer to `AUTHENTICATION_SETUP.md` for general authentication setup
- Check provider documentation for redirect URI registration procedures

This enhancement provides much more detailed information about authentication provider health and allows for flexible redirect-URI configuration, making it easier to diagnose and resolve authentication issues in your application.