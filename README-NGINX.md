# Nginx Setup for Theia01 Governance Tool

This document describes the Nginx setup script and configuration for the Theia01 Governance Tool.

## Overview

The `setup-nginx.sh` script configures Nginx as a reverse proxy for your Java application with the following features:

- **HTTPS Support**: Automatic SSL certificate generation (self-signed) with production-ready SSL configuration
- **Security Headers**: Comprehensive security headers including HSTS, CSP, XSS protection
- **Rate Limiting**: Protection against brute force attacks and API abuse
- **Microsoft SSO Integration**: Pre-configured endpoints and headers for Azure AD integration
- **Performance Optimization**: Gzip compression, static asset caching, and connection keep-alive
- **Logging**: Structured logging with automatic log rotation

## Quick Start

1. **Make the script executable:**
   ```bash
   chmod +x setup-nginx.sh
   ```

2. **Run the script as root:**
   ```bash
   sudo ./setup-nginx.sh
   ```
   
   Or with a specific domain:
   ```bash
   sudo ./setup-nginx.sh --domain governance.example.com
   ```

## Prerequisites

- Ubuntu/Debian/CentOS/RHEL system with sudo access
- Nginx installed (`sudo apt install nginx` or `sudo yum install nginx`)
- Your Java application running on localhost:8080
- Domain name pointing to your server (for production)

## What the Script Does

### 1. SSL Certificate Setup
- Creates SSL certificate directories
- Generates self-signed certificate for testing
- Sets proper file permissions

### 2. Nginx Configuration
Creates a comprehensive Nginx configuration with:
- HTTP to HTTPS redirect
- Modern SSL/TLS configuration (TLS 1.2/1.3)
- Security headers and Content Security Policy
- Rate limiting for different endpoint types
- Upstream configuration with health checks

### 3. Site Activation
- Places configuration in `/etc/nginx/sites-available/`
- Creates symlink in `/etc/nginx/sites-enabled/`
- Tests configuration before activation

### 4. Security Enhancements
- Firewall configuration (UFW/FirewallD)
- Log rotation setup
- File permission hardening

### 5. Microsoft SSO Preparation
- OAuth2 endpoint configuration
- CSP headers allowing Microsoft domains
- Integration documentation

## Configuration Details

### Rate Limiting Zones
- **Login endpoints** (`/login`): 5 requests per minute
- **API endpoints** (`/api/`): 100 requests per minute
- **General endpoints**: 50 requests per minute
- **Admin endpoints** (`/admin/`): 3 requests per minute

### Security Headers
```
Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
Content-Security-Policy: [Configured for Microsoft SSO]
```

### Special Endpoints

#### OAuth2 Support (`/oauth2/`)
- Higher burst limit for authentication flows
- Special headers for OAuth2 compatibility
- Authorization header pass-through

#### Health Checks (`/health`)
- No rate limiting
- No access logging
- Direct proxy to backend

#### Static Assets
- 1-year cache expiration
- Immutable cache headers
- Optimized for CDN compatibility

## Microsoft SSO Integration

The configuration includes specific support for Microsoft Azure AD SSO:

1. **Content Security Policy**: Allows Microsoft login domains
2. **OAuth2 Endpoints**: Special handling for authentication flows
3. **CORS Headers**: Proper cross-origin support
4. **Documentation**: Complete integration guide created

See `NGINX_SSO_INTEGRATION.md` for detailed SSO setup instructions.

## Production Deployment

### 1. SSL Certificate
Replace the self-signed certificate with a production certificate:

```bash
# Using Let's Encrypt (recommended)
sudo certbot --nginx -d your-domain.com

# Or manually replace certificates
sudo cp your-certificate.crt /etc/ssl/certs/your-domain.com.crt
sudo cp your-private-key.key /etc/ssl/private/your-domain.com.key
```

### 2. Domain Configuration
Update your DNS to point to the server's IP address.

### 3. Firewall
Ensure ports 80 and 443 are open:
```bash
sudo ufw allow 'Nginx Full'
```

### 4. Monitoring
- Check logs: `/var/log/nginx/theia01-governance_*.log`
- Monitor SSL certificate expiration
- Set up monitoring for backend health

## Troubleshooting

### Common Issues

1. **Permission Denied**
   ```bash
   sudo chmod +x setup-nginx.sh
   sudo ./setup-nginx.sh
   ```

2. **Port 8080 Not Accessible**
   - Ensure your Java application is running
   - Check firewall rules for localhost communication

3. **SSL Certificate Issues**
   - Verify certificate files exist and have correct permissions
   - Check certificate expiration: `openssl x509 -in /etc/ssl/certs/domain.crt -text -noout`

4. **Nginx Configuration Errors**
   - Test configuration: `sudo nginx -t`
   - Check syntax errors in the configuration file

### Log Files
- **Nginx Access**: `/var/log/nginx/theia01-governance_access.log`
- **Nginx Error**: `/var/log/nginx/theia01-governance_error.log`
- **System**: `journalctl -u nginx`

### Testing Commands
```bash
# Test Nginx configuration
sudo nginx -t

# Reload Nginx configuration
sudo systemctl reload nginx

# Check Nginx status
sudo systemctl status nginx

# Test SSL configuration
openssl s_client -connect your-domain.com:443 -servername your-domain.com

# Test HTTP redirect
curl -I http://your-domain.com
```

## Customization

### Modifying Rate Limits
Edit the `limit_req_zone` directives in the configuration:
```nginx
limit_req_zone $binary_remote_addr zone=api:10m rate=200r/m;  # Increase API limit
```

### Adding IP Restrictions
Uncomment and modify the IP allow/deny blocks in the admin section:
```nginx
location /admin/ {
    allow 192.168.1.0/24;    # Allow local network
    allow 10.0.0.0/8;        # Allow private network
    deny all;                # Deny everyone else
    # ... rest of configuration
}
```

### Custom Headers
Add custom headers in the appropriate location blocks:
```nginx
add_header X-Custom-Header "value" always;
```

## File Structure After Setup

```
/etc/nginx/
├── sites-available/
│   └── theia01-governance          # Main configuration
├── sites-enabled/
│   └── theia01-governance -> ../sites-available/theia01-governance
/etc/ssl/
├── certs/
│   └── your-domain.com.crt         # SSL certificate
├── private/
│   └── your-domain.com.key         # SSL private key
/var/log/nginx/
├── theia01-governance_access.log   # Access logs
└── theia01-governance_error.log    # Error logs
/etc/logrotate.d/
└── theia01-governance              # Log rotation config
```

## Support

For issues with this setup:
1. Check the troubleshooting section above
2. Review Nginx error logs
3. Verify your Java application is accessible on localhost:8080
4. Ensure proper DNS configuration for your domain

## Security Notes

- The configuration uses modern security practices
- Regular updates of Nginx and SSL certificates are recommended
- Monitor access logs for suspicious activity
- Consider implementing additional security measures like fail2ban
- Use strong passwords and proper authentication for admin endpoints