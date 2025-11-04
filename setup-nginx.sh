#!/bin/bash

# Nginx Setup Script for Theia01 Governance Tool
# This script configures Nginx for:
# - Reverse proxy to localhost:8080
# - HTTPS with SSL termination
# - Microsoft SSO integration preparation
# - Security enhancements

set -e

# Configuration variables
DOMAIN_NAME=""
APP_NAME="compliance_incubator"
NGINX_SITES_AVAILABLE="/etc/nginx/sites-available"
NGINX_SITES_ENABLED="/etc/nginx/sites-enabled"
SSL_CERT_PATH="/etc/ssl/certs"
SSL_KEY_PATH="/etc/ssl/private"
LOG_PATH="/var/log/nginx"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "This script must be run as root"
        exit 1
    fi
}

check_nginx() {
    if ! command -v nginx &> /dev/null; then
        log_error "Nginx is not installed. Please install nginx first."
        exit 1
    fi
    log_info "Nginx found: $(nginx -v 2>&1)"
}

get_domain_name() {
    if [[ -z "$DOMAIN_NAME" ]]; then
        read -p "Enter your domain name (e.g., governance.example.com): " DOMAIN_NAME
        if [[ -z "$DOMAIN_NAME" ]]; then
            log_error "Domain name is required"
            exit 1
        fi
    fi
    log_info "Using domain: $DOMAIN_NAME"
}

create_ssl_certificate() {
    log_info "Searching for existing certificate and key files in the current directory..."
    # Look for any .crt and .key files in the current directory
    cert_files=("$(find . -maxdepth 1 -type f -name "*.crt" 2>/dev/null | tr "\n" " ")")
    key_files=("$(find . -maxdepth 1 -type f -name "*.key" 2>/dev/null | tr "\n" " ")")
    
    if [[ ${#cert_files[@]} -gt 0 && ${#key_files[@]} -gt 0 ]]; then
        # Prefer files that match the domain name if available
        selected_cert=""
        selected_key=""
        for c in ${cert_files[@]}; do
            if [[ "$c" == *"$DOMAIN_NAME.crt" ]]; then
                selected_cert="$c"
                break
            fi
        done
        for k in ${key_files[@]}; do
            if [[ "$k" == *"$DOMAIN_NAME.key" ]]; then
                selected_key="$k"
                break
            fi
        done
        # If no exact match, pick the first
        if [[ -z "$selected_cert" ]]; then selected_cert="${cert_files[0]}"; fi
        if [[ -z "$selected_key" ]]; then selected_key="${key_files[0]}"; fi
        
        echo "Found the following certificate files:"; echo ${cert_files[@]}
        echo "Found the following key files:"; echo ${key_files[@]}
        read -p "Use $selected_cert and $selected_key? (y/n) " choice
        case "$choice" in
            y|Y|yes|YES)
                SSL_CERT_PATH=$(dirname "$selected_cert")
                SSL_KEY_PATH=$(dirname "$selected_key")
                log_info "Using existing certificates from $(pwd)."
                ;;
            *)
                log_info "Will create certificates in default locations."
                ;;
        esac
    else
        log_info "No matching certificate/key pair found in the current directory."
    fi

    log_info "Creating SSL certificate directories if needed..."
    mkdir -p "$SSL_CERT_PATH"
    mkdir -p "$SSL_KEY_PATH"
    
    if [[ ! -f "$SSL_CERT_PATH/$DOMAIN_NAME.crt" ]]; then
        log_warn "SSL certificate not found. Creating self-signed certificate..."
        log_warn "For production, please replace with a proper certificate (Let's Encrypt recommended)"
        
        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout "$SSL_KEY_PATH/$DOMAIN_NAME.key" \
            -out "$SSL_CERT_PATH/$DOMAIN_NAME.crt" \
            -subj "/C=US/ST=State/L=City/O=Organization/CN=$DOMAIN_NAME"
        
        chmod 600 "$SSL_KEY_PATH/$DOMAIN_NAME.key"
        chmod 644 "$SSL_CERT_PATH/$DOMAIN_NAME.crt"
        
        log_info "Self-signed certificate created successfully"
    else
        log_info "SSL certificate already exists"
    fi
}

create_nginx_config() {
    log_info "Creating Nginx configuration..."
    
    cat > "$NGINX_SITES_AVAILABLE/$APP_NAME" << EOF
# Theia01 Governance Tool - Nginx Configuration
# Configured for HTTPS, security headers, and Microsoft SSO support

# Rate limiting zones
limit_req_zone \$binary_remote_addr zone=login:10m rate=5r/m;
limit_req_zone \$binary_remote_addr zone=api:10m rate=100r/m;
limit_req_zone \$binary_remote_addr zone=general:10m rate=50r/m;

# Upstream configuration for the Java application
upstream theia01_backend {
    server 127.0.0.1:8080 fail_timeout=30s max_fails=3;
    keepalive 32;
}

# HTTP server - redirect to HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN_NAME;
    
    # Security headers even for redirects
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header X-XSS-Protection "1; mode=block" always;
    
    # Redirect all HTTP requests to HTTPS
    return 301 https://\$server_name\$request_uri;
}

# HTTPS server
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN_NAME;
    
    # SSL Configuration
    ssl_certificate $SSL_CERT_PATH/$DOMAIN_NAME.crt;
    ssl_certificate_key $SSL_KEY_PATH/$DOMAIN_NAME.key;
    
    # Modern SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;
    
    # HSTS (HTTP Strict Transport Security)
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    
    # Security headers
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options SAMEORIGIN always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://login.microsoftonline.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self' https://login.microsoftonline.com https://graph.microsoft.com; frame-src https://login.microsoftonline.com;" always;
    
    # Remove server signature
    server_tokens off;
    
    # Logging
    access_log $LOG_PATH/${APP_NAME}_access.log combined;
    error_log $LOG_PATH/${APP_NAME}_error.log warn;
    
    # Client settings
    client_max_body_size 10M;
    client_body_timeout 60s;
    client_header_timeout 60s;
    
    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/javascript
        application/json
        application/xml+rss
        application/atom+xml
        image/svg+xml;
    
    # Rate limiting for sensitive endpoints
    location /login {
        limit_req zone=login burst=5 nodelay;
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
    }
    
    # Microsoft OAuth2 endpoints (for SSO integration)
    location /oauth2/ {
        limit_req zone=login burst=10 nodelay;
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
        
        # Additional headers for OAuth2
        proxy_set_header Authorization \$http_authorization;
        proxy_pass_header Authorization;
    }
    
    # API endpoints with rate limiting
    location /api/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
        
        # API-specific headers
        proxy_set_header Accept \$http_accept;
        proxy_set_header Content-Type \$content_type;
    }
    
    # Admin endpoints with stricter rate limiting
    location /admin/ {
        limit_req zone=login burst=3 nodelay;
        
        # Optional: Restrict to specific IP ranges
        # allow 192.168.1.0/24;
        # allow 10.0.0.0/8;
        # deny all;
        
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
    }
    
    # Static assets with caching
    location ~* \.(css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)\$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        add_header X-Content-Type-Options nosniff always;
        
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # Health check endpoint (no rate limiting)
    location /health {
        access_log off;
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    # Default location for all other requests
    location / {
        limit_req zone=general burst=10 nodelay;
        
        proxy_pass http://theia01_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
        
        # Timeout settings
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # Buffer settings
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
        proxy_busy_buffers_size 8k;
        
        # Keep alive
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
    
    # Security: Block access to sensitive files
    location ~ /\. {
        deny all;
        access_log off;
        log_not_found off;
    }
    
    location ~ ~\$ {
        deny all;
        access_log off;
        log_not_found off;
    }
    
    # Deny access to configuration files
    location ~ \.(conf|ini|log|bak|sql|sh)\$ {
        deny all;
        access_log off;
        log_not_found off;
    }
}
EOF
    
    log_info "Nginx configuration created at $NGINX_SITES_AVAILABLE/$APP_NAME"
}

enable_site() {
    log_info "Enabling site configuration..."
    
    # Create symlink to sites-enabled
    if [[ -L "$NGINX_SITES_ENABLED/$APP_NAME" ]]; then
        log_info "Site already enabled"
    else
        ln -s "$NGINX_SITES_AVAILABLE/$APP_NAME" "$NGINX_SITES_ENABLED/$APP_NAME"
        log_info "Site enabled successfully"
    fi
}

test_nginx_config() {
    log_info "Testing Nginx configuration..."
    if nginx -t; then
        log_info "Nginx configuration test passed"
    else
        log_error "Nginx configuration test failed"
        exit 1
    fi
}

create_firewall_rules() {
    log_info "Setting up firewall rules..."
    
    if command -v ufw &> /dev/null; then
        ufw allow 'Nginx Full'
        ufw --force enable
        log_info "UFW firewall rules configured"
    elif command -v firewall-cmd &> /dev/null; then
        firewall-cmd --permanent --add-service=http
        firewall-cmd --permanent --add-service=https
        firewall-cmd --reload
        log_info "FirewallD rules configured"
    else
        log_warn "No supported firewall found. Please manually configure firewall to allow HTTP (80) and HTTPS (443)"
    fi
}

create_logrotate_config() {
    log_info "Creating logrotate configuration..."
    
    cat > "/etc/logrotate.d/$APP_NAME" << EOF
$LOG_PATH/${APP_NAME}_*.log {
    daily
    missingok
    rotate 52
    compress
    delaycompress
    notifempty
    create 0644 www-data adm
    postrotate
        if [ -f /var/run/nginx.pid ]; then
            kill -USR1 \$(cat /var/run/nginx.pid)
        fi
    endscript
}
EOF
    
    log_info "Logrotate configuration created"
}

create_sso_readme() {
    log_info "Creating SSO integration README..."
    
    cat > "NGINX_SSO_INTEGRATION.md" << EOF
# Microsoft SSO Integration with Nginx

This document describes how to integrate Microsoft Azure AD SSO with your Nginx-proxied Theia01 application.

## Prerequisites

1. Azure AD application registration
2. Proper OAuth2 configuration in your Java application
3. SSL certificate (production-ready)

## Azure AD Configuration

### 1. App Registration
- Go to Azure Portal > Azure Active Directory > App registrations
- Click "New registration"
- Name: "Theia01 Governance Tool"
- Redirect URI: https://$DOMAIN_NAME/oauth2/callback

### 2. Authentication Settings
- Platform configurations: Web
- Redirect URIs: 
  - https://$DOMAIN_NAME/oauth2/callback
  - https://$DOMAIN_NAME/login/oauth2/code/azure
- Front-channel logout URL: https://$DOMAIN_NAME/logout
- Implicit grant: Enable ID tokens

### 3. API Permissions
Add the following Microsoft Graph permissions:
- User.Read (Delegated)
- email (Delegated)
- openid (Delegated)
- profile (Delegated)

### 4. Certificates & Secrets
- Create a new client secret
- Note down the secret value (you'll need it for application configuration)

## Application Configuration

Add the following to your application.yml or application.properties:

\`\`\`yaml
spring:
  security:
    oauth2:
      client:
        registration:
          azure:
            client-id: \${AZURE_CLIENT_ID}
            client-secret: \${AZURE_CLIENT_SECRET}
            scope:
              - openid
              - profile
              - email
              - User.Read
            authorization-grant-type: authorization_code
            redirect-uri: "https://$DOMAIN_NAME/oauth2/callback"
        provider:
          azure:
            authorization-uri: https://login.microsoftonline.com/\${AZURE_TENANT_ID}/oauth2/v2.0/authorize
            token-uri: https://login.microsoftonline.com/\${AZURE_TENANT_ID}/oauth2/v2.0/token
            user-info-uri: https://graph.microsoft.com/v1.0/me
            jwk-set-uri: https://login.microsoftonline.com/\${AZURE_TENANT_ID}/discovery/v2.0/keys
            user-name-attribute: userPrincipalName
\`\`\`

## Environment Variables

Set the following environment variables:
- AZURE_CLIENT_ID: Your Azure AD application client ID
- AZURE_CLIENT_SECRET: Your Azure AD application client secret
- AZURE_TENANT_ID: Your Azure AD tenant ID

## Nginx Configuration Notes

The current Nginx configuration includes:

1. **CSP Headers**: Content Security Policy allows Microsoft login domains
2. **OAuth2 Endpoints**: Special handling for /oauth2/ paths
3. **Security Headers**: HTTPS enforcement and security headers
4. **Rate Limiting**: Protection against brute force attacks

## Testing SSO Integration

1. Ensure your Java application is running on localhost:8080
2. Access https://$DOMAIN_NAME
3. Click login or navigate to a protected resource
4. You should be redirected to Microsoft login
5. After authentication, you'll be redirected back to your application

## Troubleshooting

### Common Issues:

1. **Redirect URI Mismatch**: Ensure the redirect URI in Azure matches exactly
2. **HTTPS Required**: Microsoft SSO requires HTTPS in production
3. **CORS Issues**: Check that your application allows the Microsoft domains
4. **Token Validation**: Ensure your application can validate Microsoft JWT tokens

### Logs to Check:
- Nginx access logs: /var/log/nginx/${APP_NAME}_access.log
- Nginx error logs: /var/log/nginx/${APP_NAME}_error.log
- Application logs: Check your Java application logs

## Security Considerations

1. **Client Secret**: Store securely, never commit to version control
2. **HTTPS Only**: Always use HTTPS in production
3. **Token Storage**: Use secure session storage
4. **Regular Updates**: Keep OAuth2 libraries updated
5. **Monitoring**: Monitor for unusual authentication patterns

## Additional Resources

- [Microsoft Identity Platform Documentation](https://docs.microsoft.com/en-us/azure/active-directory/develop/)
- [Spring Security OAuth2 Documentation](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#oauth2)
EOF
    
    log_info "SSO integration guide created: NGINX_SSO_INTEGRATION.md"
}

restart_nginx() {
    log_info "Restarting Nginx..."
    systemctl restart nginx
    systemctl enable nginx
    log_info "Nginx restarted and enabled"
}

print_summary() {
    echo
    log_info "=== Nginx Setup Complete ==="
    echo
    echo "Configuration Details:"
    echo "  - Domain: $DOMAIN_NAME"
    echo "  - Backend: localhost:8080"
    echo "  - HTTPS: Enabled"
    echo "  - SSL Certificate: $SSL_CERT_PATH/$DOMAIN_NAME.crt"
    echo "  - Configuration: $NGINX_SITES_AVAILABLE/$APP_NAME"
    echo
    echo "Security Features Enabled:"
    echo "  ✓ HTTPS redirect"
    echo "  ✓ Security headers (HSTS, XSS Protection, etc.)"
    echo "  ✓ Rate limiting"
    echo "  ✓ Content Security Policy"
    echo "  ✓ Gzip compression"
    echo "  ✓ Static asset caching"
    echo
    echo "Microsoft SSO Support:"
    echo "  ✓ OAuth2 endpoint configuration"
    echo "  ✓ CSP headers for Microsoft domains"
    echo "  ✓ Integration guide: NGINX_SSO_INTEGRATION.md"
    echo
    log_warn "Next Steps:"
    echo "  1. Replace self-signed certificate with production certificate"
    echo "  2. Configure Microsoft Azure AD (see NGINX_SSO_INTEGRATION.md)"
    echo "  3. Update your Java application with OAuth2 configuration"
    echo "  4. Test the setup: https://$DOMAIN_NAME"
    echo
}

# Main execution
main() {
    log_info "Starting Nginx setup for Theia01 Governance Tool..."
    
    check_root
    check_nginx
    get_domain_name
    create_ssl_certificate
    create_nginx_config
    enable_site
    test_nginx_config
    create_firewall_rules
    create_logrotate_config
    create_sso_readme
    restart_nginx
    print_summary
    
    log_info "Setup completed successfully!"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--domain)
            DOMAIN_NAME="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -d, --domain DOMAIN    Domain name for the application"
            echo "  -h, --help            Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Run main function
main