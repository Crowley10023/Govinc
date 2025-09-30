# Dynamic Authentication Configuration Guide

## Overview

This application now supports fully dynamic authentication provider configuration. You can add, remove, and modify authentication providers at runtime through a web interface without requiring application restarts or manual configuration file editing.

## Supported Authentication Methods

### 1. Form-Based Authentication (Always Available)
- **Type**: Local authentication with in-memory user store
- **Configuration**: Users defined in `application.properties`
- **Status**: Always available as fallback
- **Users**: Configure via `users.*` properties

### 2. Keycloak OAuth2 (Dynamic)
- **Type**: OpenID Connect / OAuth2
- **Configuration**: Dynamic via web interface
- **Requirements**: Client ID, Client Secret, Issuer URI
- **Optional**: Custom redirect URI

### 3. Microsoft Azure AD (Dynamic)
- **Type**: OAuth2
- **Configuration**: Dynamic via web interface  
- **Requirements**: Client ID, Client Secret, Tenant ID
- **Optional**: Custom redirect URI

## Configuration Interface

### Accessing the Configuration
1. Navigate to `/admin/auth-config` in your browser
2. Log in with form authentication (admin/admin by default)
3. Configure OAuth2 providers as needed

### Web Interface Features
- **Real-time Status**: View current provider status and health
- **Connection Testing**: Test provider connectivity before saving
- **Auto-refresh**: Status updates every 30 seconds automatically
- **Health Monitoring**: Detailed health check information
- **Dynamic Updates**: Add/remove providers without restart

## File Structure Changes

### Removed Static Configuration
All OAuth2 properties have been **removed** from `application.properties` to prevent conflicts:
- ❌ `spring.security.oauth2.client.*` properties removed
- ❌ `iam.provider` settings removed  
- ✅ Only non-security settings remain in properties file

### New Configuration Files
- **Dynamic Storage**: Provider configurations stored in database/persistence layer
- **Runtime Configuration**: OAuth2 clients created dynamically
- **Fallback Security**: Form authentication always available

## Key Benefits

### 1. Zero-Restart Configuration
- Add new OAuth2 providers without application restart
- Modify existing provider settings on-the-fly
- Remove providers instantly

### 2. Conflict-Free Operation
- No more Spring Boot OAuth2 auto-configuration conflicts
- Properties file only contains non-security settings
- Clean separation of concerns

### 3. Administrative Control
- Web-based configuration interface
- Real-time health monitoring
- Connection testing before deployment

### 4. Production-Ready
- Always-available form authentication fallback
- Robust error handling and logging
- Health check monitoring

## Configuration Steps

### Setting Up Keycloak
1. Go to `/admin/auth-config`
2. Find the Keycloak section
3. Fill in:
   - **Client ID**: Your Keycloak client ID
   - **Client Secret**: Your Keycloak client secret  
   - **Issuer URI**: `http://your-keycloak-server:8080/realms/your-realm`
   - **Redirect URI**: (optional) Custom redirect URL
4. Click "Configure Keycloak"
5. Click "Test Connection" to verify

### Setting Up Azure AD
1. Go to `/admin/auth-config`
2. Find the Azure AD section
3. Fill in:
   - **Client ID**: Your Azure application client ID
   - **Client Secret**: Your Azure application client secret
   - **Tenant ID**: Your Azure tenant ID
   - **Redirect URI**: (optional) Custom redirect URL
4. Click "Configure Azure"
5. Click "Test Connection" to verify

### Managing Local Users
Edit `application.properties`:
```properties
# Format: users.<username>=<password>[,<email>]
users.admin=admin,admin@example.com
users.manager=manager123,manager@company.com
users.user=password,user@company.com
```

## Technical Architecture

### Dynamic Client Registration
- **DynamicClientRegistrationRepository**: Custom implementation that refreshes OAuth2 clients at runtime
- **AuthConfigService**: Manages provider lifecycle and configuration
- **Persistence Layer**: Stores provider configurations between restarts

### Security Configuration
- **SecurityConfig**: Configures Spring Security with dynamic OAuth2 support
- **LoginController**: Provides login page with available authentication options
- **Fallback Authentication**: Form-based auth always available

### Health Monitoring
- **AuthProviderHealthService**: Monitors provider connectivity
- **Real-time Status**: Web interface shows live health status
- **Connection Testing**: Validate configurations before saving

## Troubleshooting

### Provider Not Appearing on Login Page
1. Check `/admin/auth-config` for provider status
2. Ensure provider is "Healthy" and "Configured"
3. Run "Test Connection" to verify connectivity
4. Check application logs for errors

### OAuth2 Login Failures
1. Verify redirect URI configuration
2. Check provider health status
3. Ensure client credentials are correct
4. Review provider-specific logs

### Form Login Issues  
1. Verify users are configured in `application.properties`
2. Check password encoding (BCrypt used)
3. Ensure form authentication is not disabled

## Migration from Static Configuration

### If You Have Existing OAuth2 Properties
1. **Backup**: Save your current `application.properties`
2. **Note Settings**: Record your OAuth2 client IDs, secrets, etc.
3. **Apply Changes**: Use the new `application.properties` (security settings removed)
4. **Reconfigure**: Use `/admin/auth-config` to set up providers
5. **Test**: Verify all authentication methods work

### Configuration Mapping
Old property format → New web interface:
```
spring.security.oauth2.client.registration.keycloak.client-id 
  → Keycloak Client ID field

spring.security.oauth2.client.registration.keycloak.client-secret
  → Keycloak Client Secret field

spring.security.oauth2.client.provider.keycloak.issuer-uri
  → Keycloak Issuer URI field
```

## Security Considerations

### Credential Storage
- OAuth2 credentials stored in encrypted persistence layer
- No sensitive data in version-controlled properties files
- Access controlled through admin authentication

### Fallback Security
- Form authentication cannot be disabled
- Admin users always accessible via local authentication
- No risk of being locked out during OAuth2 configuration

### Network Security
- Health checks respect network firewalls
- Connection testing validates external provider access
- Detailed error reporting for troubleshooting

## Support and Monitoring

### Logging
- Authentication events logged at INFO level
- Provider health checks logged
- Configuration changes tracked
- OAuth2 errors detailed in logs

### Monitoring Endpoints
- `/admin/auth-config/status` - JSON status of all providers
- `/admin/auth-config/health/{provider}` - Individual provider health
- Real-time web interface at `/admin/auth-config`

### Performance
- Minimal overhead from dynamic configuration
- OAuth2 client registrations cached
- Health checks run asynchronously
- Auto-refresh optimized for minimal load

## Best Practices

1. **Always Test**: Use "Test Connection" before deploying
2. **Monitor Health**: Check provider status regularly
3. **Backup Configuration**: Export provider settings before major changes
4. **Gradual Rollout**: Configure one provider at a time
5. **Fallback Ready**: Ensure admin users can always login via form authentication

This dynamic authentication system provides enterprise-grade flexibility while maintaining security and reliability.