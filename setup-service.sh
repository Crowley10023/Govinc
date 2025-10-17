#!/bin/bash

# Systemd Service Setup Script for Theia01 Governance Tool
# This script creates a systemd service that automatically starts the Java application
# with proper logging, error handling, and system integration

set -e

# Configuration variables
SERVICE_NAME="theia01-governance"
SERVICE_USER="theia01"
APP_DIR=""
JAR_FILE="app/build/libs/app.jar"
JAVA_OPTS=""
SERVICE_DESCRIPTION="Theia01 Governance Tool - Compliance Management Platform"
LOG_DIR="/var/log/${SERVICE_NAME}"
PID_FILE="/var/run/${SERVICE_NAME}.pid"
WORK_DIR=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "This script must be run as root"
        exit 1
    fi
}

detect_java() {
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        log_info "Java found: $JAVA_VERSION"
        JAVA_HOME_DETECTED=$(readlink -f $(which java) | sed "s|/bin/java||")
        log_debug "Java home detected: $JAVA_HOME_DETECTED"
        return 0
    else
        log_error "Java is not installed or not in PATH"
        log_error "Please install Java 11 or higher"
        exit 1
    fi
}

get_application_directory() {
    if [[ -z "$APP_DIR" ]]; then
        # Try to detect current directory if it looks like the app directory
        if [[ -f "$(pwd)/$JAR_FILE" ]]; then
            APP_DIR="$(pwd)"
            log_info "Using current directory as application directory: $APP_DIR"
        elif [[ -f "$JAR_FILE" ]]; then
            APP_DIR="$(pwd)"
            log_info "Using current directory as application directory: $APP_DIR"
        else
            read -p "Enter the full path to the application directory: " APP_DIR
            if [[ ! -d "$APP_DIR" ]]; then
                log_error "Directory does not exist: $APP_DIR"
                exit 1
            fi
        fi
    fi
    
    WORK_DIR="$APP_DIR"
    
    # Verify JAR file exists
    if [[ ! -f "$APP_DIR/$JAR_FILE" ]]; then
        log_error "JAR file not found: $APP_DIR/$JAR_FILE"
        log_error "Please build the application first using: ./gradlew build"
        exit 1
    fi
    
    log_info "Application directory: $APP_DIR"
    log_info "JAR file: $APP_DIR/$JAR_FILE"
}

create_service_user() {
    if id "$SERVICE_USER" &>/dev/null; then
        log_info "Service user '$SERVICE_USER' already exists"
    else
        log_info "Creating service user '$SERVICE_USER'..."
        useradd --system --shell /bin/bash --home-dir "$APP_DIR" --no-create-home "$SERVICE_USER"
        log_info "Service user '$SERVICE_USER' created"
    fi
    
    # Set ownership of application directory
    log_info "Setting ownership of application directory..."
    chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"
    
    # Set proper permissions
    chmod 755 "$APP_DIR"
    find "$APP_DIR" -type f -name "*.jar" -exec chmod 644 {} \;
    find "$APP_DIR" -type f -name "*.sh" -exec chmod 755 {} \;
}

create_log_directory() {
    log_info "Creating log directory: $LOG_DIR"
    mkdir -p "$LOG_DIR"
    chown "$SERVICE_USER:$SERVICE_USER" "$LOG_DIR"
    chmod 755 "$LOG_DIR"
}

detect_system_resources() {
    # Detect available memory and CPU cores for JVM tuning
    TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
    CPU_CORES=$(nproc)
    
    log_info "System resources detected:"
    log_info "  Total memory: ${TOTAL_MEM}MB"
    log_info "  CPU cores: $CPU_CORES"
    
    # Calculate JVM memory settings (use 50% of available memory, max 2GB)
    if [[ $TOTAL_MEM -gt 4096 ]]; then
        HEAP_SIZE="2g"
    elif [[ $TOTAL_MEM -gt 2048 ]]; then
        HEAP_SIZE="1g"
    elif [[ $TOTAL_MEM -gt 1024 ]]; then
        HEAP_SIZE="512m"
    else
        HEAP_SIZE="256m"
    fi
    
    # Set default JVM options if not provided
    if [[ -z "$JAVA_OPTS" ]]; then
        JAVA_OPTS="-Xmx$HEAP_SIZE -Xms$HEAP_SIZE -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
        log_info "Generated JVM options: $JAVA_OPTS"
    fi
}

create_environment_file() {
    log_info "Creating environment file..."
    
    cat > "/etc/systemd/system/$SERVICE_NAME.env" << EOF
# Environment variables for Theia01 Governance Tool Service
# This file contains environment-specific configuration

# Java Configuration
JAVA_HOME=$JAVA_HOME_DETECTED
JAVA_OPTS=$JAVA_OPTS

# Application Configuration
APP_DIR=$APP_DIR
WORK_DIR=$WORK_DIR
LOG_DIR=$LOG_DIR

# Spring Boot Configuration
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080

# Database Configuration (if needed to override application.properties)
# DATABASE_URL=jdbc:mariadb://localhost:3306/govinc
# DATABASE_USERNAME=govinc
# DATABASE_PASSWORD=your_password

# Security Configuration
# AZURE_CLIENT_ID=your_azure_client_id
# AZURE_CLIENT_SECRET=your_azure_client_secret
# AZURE_TENANT_ID=your_azure_tenant_id

# Logging Configuration
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_GOVINC=INFO

# JVM Monitoring
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus

# File Upload Configuration
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=10MB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=10MB

# Session Configuration
SERVER_SERVLET_SESSION_TIMEOUT=30m
SERVER_SERVLET_SESSION_COOKIE_SECURE=true
SERVER_SERVLET_SESSION_COOKIE_HTTP_ONLY=true

# Custom Application Properties
# Add any custom properties here as KEY=VALUE pairs
EOF
    
    chmod 640 "/etc/systemd/system/$SERVICE_NAME.env"
    chown root:$SERVICE_USER "/etc/systemd/system/$SERVICE_NAME.env"
    log_info "Environment file created: /etc/systemd/system/$SERVICE_NAME.env"
}

create_systemd_service() {
    log_info "Creating systemd service file..."
    
    cat > "/etc/systemd/system/$SERVICE_NAME.service" << EOF
[Unit]
Description=$SERVICE_DESCRIPTION
Documentation=https://github.com/yourorg/theia01
After=network-online.target
Wants=network-online.target
ConditionFileNotEmpty=$APP_DIR/$JAR_FILE

[Service]
Type=exec
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$WORK_DIR
EnvironmentFile=/etc/systemd/system/$SERVICE_NAME.env

# Main service execution
ExecStart=$JAVA_HOME_DETECTED/bin/java \$JAVA_OPTS -jar $APP_DIR/$JAR_FILE
ExecStop=/bin/kill -TERM \$MAINPID
ExecReload=/bin/kill -HUP \$MAINPID

# Process management
Restart=always
RestartSec=10
KillMode=mixed
KillSignal=SIGTERM
TimeoutStopSec=30
StartLimitBurst=3
StartLimitIntervalSec=60

# Security settings
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=$APP_DIR $LOG_DIR /tmp
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectControlGroups=yes
RestrictRealtime=yes
RestrictNamespaces=yes
LockPersonality=yes
MemoryDenyWriteExecute=no
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
SystemCallFilter=@system-service
SystemCallErrorNumber=EPERM

# Resource limits
LimitNOFILE=65535
LimitNPROC=4096

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE_NAME

# PID file (optional, systemd manages this automatically)
PIDFile=$PID_FILE

[Install]
WantedBy=multi-user.target
EOF
    
    log_info "Systemd service file created: /etc/systemd/system/$SERVICE_NAME.service"
}

create_logrotate_config() {
    log_info "Creating logrotate configuration..."
    
    cat > "/etc/logrotate.d/$SERVICE_NAME" << EOF
$LOG_DIR/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 0644 $SERVICE_USER $SERVICE_USER
    postrotate
        systemctl kill -s USR1 $SERVICE_NAME.service 2>/dev/null || true
    endscript
    
    # Size-based rotation as backup
    size 100M
    maxage 90
}

# Application-specific logs (if any)
$APP_DIR/logs/*.log {
    daily
    missingok
    rotate 7
    compress
    delaycompress
    notifempty
    create 0644 $SERVICE_USER $SERVICE_USER
    postrotate
        systemctl kill -s USR1 $SERVICE_NAME.service 2>/dev/null || true
    endscript
}
EOF
    
    log_info "Logrotate configuration created: /etc/logrotate.d/$SERVICE_NAME"
}

create_health_check_script() {
    log_info "Creating health check script..."
    
    cat > "/usr/local/bin/$SERVICE_NAME-health-check" << EOF
#!/bin/bash
# Health check script for $SERVICE_DESCRIPTION

# Configuration
SERVICE_NAME="$SERVICE_NAME"
HEALTH_URL="http://localhost:8080/health"
TIMEOUT=10
MAX_RETRIES=3

# Function to check if service is running
check_service_status() {
    systemctl is-active "\$SERVICE_NAME" >/dev/null 2>&1
}

# Function to check HTTP health endpoint
check_http_health() {
    curl -sf --max-time "\$TIMEOUT" "\$HEALTH_URL" >/dev/null 2>&1
}

# Function to check application logs for errors
check_logs() {
    # Check for recent errors in the last 5 minutes
    journalctl -u "\$SERVICE_NAME" --since="5 minutes ago" --priority=err --quiet
    return \$?
}

# Main health check
main() {
    local exit_code=0
    
    echo "=== \$(date): Health Check for \$SERVICE_NAME ==="
    
    # Check service status
    if check_service_status; then
        echo "✓ Service is running"
    else
        echo "✗ Service is not running"
        exit_code=1
    fi
    
    # Check HTTP endpoint
    if check_http_health; then
        echo "✓ HTTP health endpoint is responding"
    else
        echo "✗ HTTP health endpoint is not responding"
        exit_code=1
    fi
    
    # Check for recent errors
    if ! check_logs; then
        echo "✓ No recent errors in logs"
    else
        echo "✗ Recent errors found in logs"
        exit_code=1
    fi
    
    # Overall status
    if [ \$exit_code -eq 0 ]; then
        echo "✓ Overall health: HEALTHY"
    else
        echo "✗ Overall health: UNHEALTHY"
    fi
    
    echo "================================"
    return \$exit_code
}

# Command line options
case "\${1:-check}" in
    check)
        main
        ;;
    status)
        systemctl status "\$SERVICE_NAME"
        ;;
    logs)
        journalctl -u "\$SERVICE_NAME" -f
        ;;
    restart)
        echo "Restarting \$SERVICE_NAME..."
        systemctl restart "\$SERVICE_NAME"
        sleep 5
        main
        ;;
    *)
        echo "Usage: \$0 {check|status|logs|restart}"
        echo "  check   - Run health check (default)"
        echo "  status  - Show service status"
        echo "  logs    - Follow service logs"
        echo "  restart - Restart service and check health"
        exit 1
        ;;
esac
EOF
    
    chmod +x "/usr/local/bin/$SERVICE_NAME-health-check"
    log_info "Health check script created: /usr/local/bin/$SERVICE_NAME-health-check"
}

setup_monitoring() {
    log_info "Setting up monitoring and alerting..."
    
    # Create a simple monitoring cron job
    cat > "/etc/cron.d/$SERVICE_NAME-monitor" << EOF
# Monitor Theia01 Governance Tool every 5 minutes
*/5 * * * * root /usr/local/bin/$SERVICE_NAME-health-check check >/dev/null || echo "\$(date): $SERVICE_DESCRIPTION health check failed" >> /var/log/$SERVICE_NAME-monitor.log
EOF
    
    log_info "Monitoring cron job created: /etc/cron.d/$SERVICE_NAME-monitor"
}

create_management_script() {
    log_info "Creating service management script..."
    
    cat > "/usr/local/bin/$SERVICE_NAME-manage" << EOF
#!/bin/bash
# Management script for $SERVICE_DESCRIPTION

SERVICE_NAME="$SERVICE_NAME"
APP_DIR="$APP_DIR"
JAR_FILE="$JAR_FILE"

case "\$1" in
    start)
        echo "Starting \$SERVICE_NAME..."
        systemctl start "\$SERVICE_NAME"
        ;;
    stop)
        echo "Stopping \$SERVICE_NAME..."
        systemctl stop "\$SERVICE_NAME"
        ;;
    restart)
        echo "Restarting \$SERVICE_NAME..."
        systemctl restart "\$SERVICE_NAME"
        ;;
    reload)
        echo "Reloading \$SERVICE_NAME..."
        systemctl reload "\$SERVICE_NAME"
        ;;
    status)
        systemctl status "\$SERVICE_NAME"
        ;;
    enable)
        echo "Enabling \$SERVICE_NAME to start at boot..."
        systemctl enable "\$SERVICE_NAME"
        ;;
    disable)
        echo "Disabling \$SERVICE_NAME from starting at boot..."
        systemctl disable "\$SERVICE_NAME"
        ;;
    logs)
        journalctl -u "\$SERVICE_NAME" -f
        ;;
    health)
        /usr/local/bin/\$SERVICE_NAME-health-check
        ;;
    rebuild)
        echo "Rebuilding application..."
        cd "\$APP_DIR"
        sudo -u $SERVICE_USER ./gradlew build
        if [ \$? -eq 0 ]; then
            echo "Build successful. Restarting service..."
            systemctl restart "\$SERVICE_NAME"
        else
            echo "Build failed!"
            exit 1
        fi
        ;;
    update-env)
        echo "Current environment file:"
        cat "/etc/systemd/system/\$SERVICE_NAME.env"
        echo ""
        read -p "Press Enter to edit with nano, or Ctrl+C to cancel..."
        nano "/etc/systemd/system/\$SERVICE_NAME.env"
        echo "Reloading systemd and restarting service..."
        systemctl daemon-reload
        systemctl restart "\$SERVICE_NAME"
        ;;
    *)
        echo "Usage: \$0 {start|stop|restart|reload|status|enable|disable|logs|health|rebuild|update-env}"
        echo ""
        echo "Commands:"
        echo "  start      - Start the service"
        echo "  stop       - Stop the service"
        echo "  restart    - Restart the service"
        echo "  reload     - Reload the service configuration"
        echo "  status     - Show service status"
        echo "  enable     - Enable service to start at boot"
        echo "  disable    - Disable service from starting at boot"
        echo "  logs       - Follow service logs"
        echo "  health     - Run health check"
        echo "  rebuild    - Rebuild application and restart"
        echo "  update-env - Edit environment configuration"
        exit 1
        ;;
esac
EOF
    
    chmod +x "/usr/local/bin/$SERVICE_NAME-manage"
    log_info "Management script created: /usr/local/bin/$SERVICE_NAME-manage"
}

enable_and_start_service() {
    log_info "Reloading systemd daemon..."
    systemctl daemon-reload
    
    log_info "Enabling service to start at boot..."
    systemctl enable "$SERVICE_NAME"
    
    log_info "Starting service..."
    systemctl start "$SERVICE_NAME"
    
    # Wait a moment for service to start
    sleep 3
    
    # Check service status
    if systemctl is-active "$SERVICE_NAME" >/dev/null 2>&1; then
        log_info "Service started successfully!"
    else
        log_error "Service failed to start. Checking status..."
        systemctl status "$SERVICE_NAME" --no-pager
        exit 1
    fi
}

create_readme() {
    log_info "Creating service documentation..."
    
    cat > "$APP_DIR/SERVICE_README.md" << EOF
# Theia01 Governance Tool - System Service

The Theia01 Governance Tool has been configured as a system service that automatically starts at boot.

## Service Information
- **Service Name**: $SERVICE_NAME
- **Service User**: $SERVICE_USER
- **Application Directory**: $APP_DIR
- **Log Directory**: $LOG_DIR
- **Service Port**: 8080

## Management Commands

### Using the management script (recommended):
\`\`\`bash
# Start the service
sudo $SERVICE_NAME-manage start

# Stop the service
sudo $SERVICE_NAME-manage stop

# Restart the service
sudo $SERVICE_NAME-manage restart

# Check service status
sudo $SERVICE_NAME-manage status

# View logs
sudo $SERVICE_NAME-manage logs

# Run health check
sudo $SERVICE_NAME-manage health

# Rebuild application
sudo $SERVICE_NAME-manage rebuild

# Edit environment configuration
sudo $SERVICE_NAME-manage update-env
\`\`\`

### Using systemctl directly:
\`\`\`bash
# Start the service
sudo systemctl start $SERVICE_NAME

# Stop the service
sudo systemctl stop $SERVICE_NAME

# Restart the service
sudo systemctl restart $SERVICE_NAME

# Check status
sudo systemctl status $SERVICE_NAME

# Enable/disable auto-start at boot
sudo systemctl enable $SERVICE_NAME
sudo systemctl disable $SERVICE_NAME

# View logs
sudo journalctl -u $SERVICE_NAME -f
\`\`\`

## Health Monitoring

The service includes automatic health monitoring:
- Health checks run every 5 minutes
- Failed checks are logged to: /var/log/$SERVICE_NAME-monitor.log
- Manual health check: \`sudo $SERVICE_NAME-health-check\`

## Configuration Files

- **Service Definition**: /etc/systemd/system/$SERVICE_NAME.service
- **Environment Variables**: /etc/systemd/system/$SERVICE_NAME.env
- **Log Rotation**: /etc/logrotate.d/$SERVICE_NAME
- **Monitoring**: /etc/cron.d/$SERVICE_NAME-monitor

## Logs

- **Service Logs**: \`sudo journalctl -u $SERVICE_NAME\`
- **Application Logs**: $LOG_DIR/
- **Monitor Logs**: /var/log/$SERVICE_NAME-monitor.log

## Environment Configuration

Edit environment variables in: /etc/systemd/system/$SERVICE_NAME.env

Common variables to configure:
- JAVA_OPTS: JVM options
- SPRING_PROFILES_ACTIVE: Active Spring profiles
- Database connection settings
- Azure AD/OAuth2 settings

After editing environment variables, reload and restart:
\`\`\`bash
sudo systemctl daemon-reload
sudo systemctl restart $SERVICE_NAME
\`\`\`

## Security Features

The service runs with enhanced security:
- Dedicated service user ($SERVICE_USER)
- Restricted file system access
- Limited system capabilities
- No new privileges allowed
- Protected kernel access

## Troubleshooting

### Service won't start
1. Check service status: \`sudo systemctl status $SERVICE_NAME\`
2. Check logs: \`sudo journalctl -u $SERVICE_NAME\`
3. Verify JAR file exists: \`ls -la $APP_DIR/$JAR_FILE\`
4. Check Java installation: \`java -version\`

### Application not accessible
1. Check if service is running: \`sudo systemctl is-active $SERVICE_NAME\`
2. Verify port 8080 is listening: \`sudo netstat -tlnp | grep 8080\`
3. Check firewall rules: \`sudo ufw status\`
4. Run health check: \`sudo $SERVICE_NAME-health-check\`

### Database connection issues
1. Edit environment file: \`sudo $SERVICE_NAME-manage update-env\`
2. Update database settings
3. Restart service: \`sudo systemctl restart $SERVICE_NAME\`

### Performance issues
1. Check system resources: \`top\`, \`free -h\`
2. Adjust JVM options in environment file
3. Monitor GC logs if enabled
4. Check for memory leaks: \`sudo journalctl -u $SERVICE_NAME | grep -i "out of memory"\`

## Updating the Application

1. Build new version: \`cd $APP_DIR && ./gradlew build\`
2. Restart service: \`sudo systemctl restart $SERVICE_NAME\`

Or use the management script: \`sudo $SERVICE_NAME-manage rebuild\`

## Backup Recommendations

Regular backups should include:
- Application directory: $APP_DIR
- Environment configuration: /etc/systemd/system/$SERVICE_NAME.env
- Database (if using external database)
- Log files: $LOG_DIR

## Support

For service-related issues:
1. Check this documentation
2. Review system logs: \`sudo journalctl -u $SERVICE_NAME\`
3. Run health diagnostics: \`sudo $SERVICE_NAME-health-check\`
4. Check application-specific documentation
EOF
    
    chown $SERVICE_USER:$SERVICE_USER "$APP_DIR/SERVICE_README.md"
    log_info "Service documentation created: $APP_DIR/SERVICE_README.md"
}

print_summary() {
    echo
    log_info "=== System Service Setup Complete ==="
    echo
    echo "Service Configuration:"
    echo "  - Service Name: $SERVICE_NAME"
    echo "  - Service User: $SERVICE_USER"
    echo "  - Application Directory: $APP_DIR"
    echo "  - JAR File: $APP_DIR/$JAR_FILE"
    echo "  - Log Directory: $LOG_DIR"
    echo "  - Java Options: $JAVA_OPTS"
    echo
    echo "Files Created:"
    echo "  ✓ /etc/systemd/system/$SERVICE_NAME.service"
    echo "  ✓ /etc/systemd/system/$SERVICE_NAME.env"
    echo "  ✓ /etc/logrotate.d/$SERVICE_NAME"
    echo "  ✓ /etc/cron.d/$SERVICE_NAME-monitor"
    echo "  ✓ /usr/local/bin/$SERVICE_NAME-manage"
    echo "  ✓ /usr/local/bin/$SERVICE_NAME-health-check"
    echo "  ✓ $APP_DIR/SERVICE_README.md"
    echo
    echo "Service Status:"
    systemctl status "$SERVICE_NAME" --no-pager --lines=5
    echo
    log_info "Management Commands:"
    echo "  sudo $SERVICE_NAME-manage {start|stop|restart|status|logs|health}"
    echo "  sudo systemctl {start|stop|restart|status} $SERVICE_NAME"
    echo "  sudo $SERVICE_NAME-health-check"
    echo
    log_info "Next Steps:"
    echo "  1. Verify service is running: sudo $SERVICE_NAME-manage status"
    echo "  2. Test application: curl http://localhost:8080/health"
    echo "  3. Configure environment: sudo $SERVICE_NAME-manage update-env"
    echo "  4. Read documentation: cat $APP_DIR/SERVICE_README.md"
    echo
}

# Main execution
main() {
    log_info "Starting system service setup for Theia01 Governance Tool..."
    
    check_root
    detect_java
    get_application_directory
    detect_system_resources
    create_service_user
    create_log_directory
    create_environment_file
    create_systemd_service
    create_logrotate_config
    create_health_check_script
    setup_monitoring
    create_management_script
    enable_and_start_service
    create_readme
    print_summary
    
    log_info "Setup completed successfully!"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--directory)
            APP_DIR="$2"
            shift 2
            ;;
        -j|--java-opts)
            JAVA_OPTS="$2"
            shift 2
            ;;
        -u|--user)
            SERVICE_USER="$2"
            shift 2
            ;;
        -n|--name)
            SERVICE_NAME="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -d, --directory DIR    Application directory (auto-detected if not specified)"
            echo "  -j, --java-opts OPTS   JVM options (auto-generated if not specified)"
            echo "  -u, --user USER        Service user name (default: theia01)"
            echo "  -n, --name NAME        Service name (default: theia01-governance)"
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