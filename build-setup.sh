#!/bin/bash

# Build Setup Script for Theia01 Governance Tool
# This script handles database setup and application build
#
# REQUIREMENTS:
# - Must be run with sudo (./build-setup.sh or sudo ./build-setup.sh)
# - Must have execute permission: chmod +x build-setup.sh
# - Creates .gitignored config file for credential reuse

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_NAME="govinc"
DB_USER="govinc"
MARIADB_PORT=3306
APPLICATION_PROPS="app/src/main/resources/application.properties"
BUILD_CONFIG_FILE=".build-setup.local"  # Git ignored file for storing user entries

# Function to check if running with sudo
check_sudo() {
    if [ "$EUID" -ne 0 ]; then
        echo -e "${RED}Error: This script must be run with sudo${NC}"
        echo -e "${YELLOW}Usage: sudo ./build-setup.sh${NC}"
        exit 1
    fi
}

# Function to load saved configuration
load_saved_config() {
    if [ -f "$BUILD_CONFIG_FILE" ]; then
        echo -e "${YELLOW}Found saved configuration file: $BUILD_CONFIG_FILE${NC}"
        # shellcheck source=.build-setup.local
        source "$BUILD_CONFIG_FILE"
        echo -e "${GREEN}✓ Loaded saved configuration${NC}"
        return 0
    fi
    return 1
}

# Function to save configuration for reuse
save_config() {
    local host="$1"
    local port="$2"
    local db_name="$3"
    local db_user="$4"
    local db_password="$5"
    local target_dir="$6"
    local service_name="$7"
    
    cat > "$BUILD_CONFIG_FILE" << EOF
# Build Setup Configuration - Auto-generated
# This file is git-ignored and stores user entries for reuse
DB_HOST="$host"
DB_PORT="$port"
DB_NAME="$db_name"
DB_USER="$db_user"
DB_PASSWORD="$db_password"
TARGET_DIRECTORY="$target_dir"
SERVICE_NAME="$service_name"
EOF
    chmod 600 "$BUILD_CONFIG_FILE"
    echo -e "${GREEN}✓ Configuration saved to $BUILD_CONFIG_FILE${NC}"
}

# Function to create MySQL config file
create_mysql_config_file() {
    local user="$1"
    local password="$2"
    local host="$3"
    local port="$4"
    local config_file="$5"
    
    cat > "$config_file" << EOF
[client]
user=$user
password=$password
host=$host
port=$port
EOF
    chmod 600 "$config_file"
}

# Function to clean up temporary config file
cleanup_mysql_config() {
    local config_file="$1"
    if [ -f "$config_file" ]; then
        rm -f "$config_file"
    fi
}

# Function to execute MySQL command with temporary config file
execute_mysql_with_config() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local mysql_command="$5"
    local timeout_duration="${6:-30}"
    
    # Create temporary config file
    local temp_config
    temp_config=$(mktemp -t mysql_config_XXXXXX.cnf)
    
    # Set up cleanup trap
    trap "cleanup_mysql_config '$temp_config'" RETURN EXIT INT TERM
    
    # Create config file
    create_mysql_config_file "$user" "$password" "$host" "$port" "$temp_config"
    
    # Execute MySQL command with timeout
    local result
    local timeout_cmd="timeout $timeout_duration"
    if ! command -v timeout &> /dev/null; then
        timeout_cmd=""
    fi
    
    if [ -n "$timeout_cmd" ]; then
        result=$($timeout_cmd mysql --defaults-file="$temp_config" -e "$mysql_command" 2>&1)
    else
        result=$(mysql --defaults-file="$temp_config" -e "$mysql_command" 2>&1)
    fi
    local exit_code=$?
    
    # Clean up
    cleanup_mysql_config "$temp_config"
    
    # Return result
    echo "$result"
    return $exit_code
}

# Function to test if MySQL server is reachable
test_mysql_server_reachable() {
    local host="$1"
    local port="$2"
    
    echo -e "${YELLOW}Testing if MySQL server is reachable at $host:$port...${NC}"
    
    local server_test
    server_test=$(mysql -h"$host" -P"$port" -u"nonexistent_user" --connect-timeout=5 -e "SELECT 1;" 2>&1 || true)
    
    if echo "$server_test" | grep -qi "Access denied"; then
        echo -e "${GREEN}  ✓ MySQL server is reachable (authentication required)${NC}"
        return 0
    elif echo "$server_test" | grep -qi "Can't connect"; then
        echo -e "${RED}  ✗ Cannot reach MySQL server${NC}"
        echo -e "${RED}  Server may be down or host/port incorrect${NC}"
        return 1
    elif echo "$server_test" | grep -qi "Unknown database"; then
        echo -e "${GREEN}  ✓ MySQL server is reachable${NC}"
        return 0
    else
        echo -e "${YELLOW}  ? Server response unclear: $server_test${NC}"
        return 0
    fi
}

# Function to check if MySQL client is installed
check_mysql_client_installed() {
    if command -v mysql &> /dev/null; then
        echo -e "${GREEN}✓ MySQL client found: $(which mysql)${NC}"
        mysql --version 2>/dev/null || echo -e "${YELLOW}  Warning: Could not get MySQL client version${NC}"
        return 0
    elif command -v mariadb &> /dev/null; then
        echo -e "${GREEN}✓ MariaDB client found: $(which mariadb)${NC}"
        mariadb --version 2>/dev/null || echo -e "${YELLOW}  Warning: Could not get MariaDB client version${NC}"
        return 0
    else
        echo -e "${RED}✗ No MySQL/MariaDB client found${NC}"
        echo -e "${YELLOW}  Install with:${NC}"
        echo -e "${YELLOW}    Ubuntu/Debian: sudo apt-get install mysql-client${NC}"
        echo -e "${YELLOW}    RHEL/CentOS:   sudo yum install mysql${NC}"
        echo -e "${YELLOW}    macOS:         brew install mysql-client${NC}"
        return 1
    fi
}

# Function to check if database exists
check_database_exists() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local database="$5"
    
    # Create temporary config file
    local temp_config
    temp_config=$(mktemp -t mysql_config_XXXXXX.cnf)
    
    # Set up cleanup trap
    trap "cleanup_mysql_config '$temp_config'" RETURN EXIT INT TERM
    
    # Create config file
    create_mysql_config_file "$user" "$password" "$host" "$port" "$temp_config"
    
    # Execute command
    local output
    output=$(mysql --defaults-file="$temp_config" -e "SHOW DATABASES LIKE '$database';" 2>/dev/null)
    local result=$?
    
    # Clean up
    cleanup_mysql_config "$temp_config"
    
    # Check if the command succeeded and if the database name appears in output
    if [ $result -eq 0 ] && [[ -n "$output" ]] && echo "$output" | grep -q "$database"; then
        return 0  # Database exists
    else
        return 1  # Database doesn't exist or connection failed
    fi
}

# Function to test database connection
test_db_connection() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local database="$5"
    local check_db_exists="${6:-true}"
    
    echo -e "${YELLOW}Testing database connection to $host:$port...${NC}"
    echo -e "${BLUE}  User: $user${NC}"
    echo -e "${BLUE}  Database: $database${NC}"
    
    # Check if MySQL client is available
    if ! command -v mysql &> /dev/null; then
        echo -e "${RED}✗ MySQL client not found!${NC}"
        return 1
    fi
    
    local timeout_cmd="timeout 30"
    if ! command -v timeout &> /dev/null; then
        timeout_cmd=""
    fi
    
    echo -e "${YELLOW}  Step 1: Testing basic connection...${NC}"
    
    # Create temporary config file
    local temp_config
    temp_config=$(mktemp -t mysql_config_XXXXXX.cnf)
    
    # Set up cleanup trap
    trap "cleanup_mysql_config '$temp_config'" RETURN EXIT INT TERM
    
    # Create config file
    create_mysql_config_file "$user" "$password" "$host" "$port" "$temp_config"
    
    local connection_test
    if [ -n "$timeout_cmd" ]; then
        connection_test=$($timeout_cmd mysql --defaults-file="$temp_config" --connect-timeout=10 -e "SELECT 1 AS test;" 2>&1)
    else
        connection_test=$(mysql --defaults-file="$temp_config" --connect-timeout=10 -e "SELECT 1 AS test;" 2>&1)
    fi
    local conn_result=$?
    
    if [ $conn_result -eq 124 ]; then
        echo -e "${RED}✗ Database connection timed out!${NC}"
        return 1
    elif [ $conn_result -ne 0 ]; then
        echo -e "${RED}✗ Database connection failed!${NC}"
        
        if echo "$connection_test" | grep -qi "Access denied"; then
            echo -e "${RED}  Error: Access denied - Invalid username or password${NC}"
        elif echo "$connection_test" | grep -qi "Can't connect to"; then
            echo -e "${RED}  Error: Cannot connect to database server${NC}"
        else
            echo -e "${RED}  Error details: $connection_test${NC}"
        fi
        return 1
    fi
    
    echo -e "${GREEN}  ✓ Basic connection successful${NC}"
    
    if [[ "$check_db_exists" == "true" ]]; then
        echo -e "${YELLOW}  Step 2: Checking if database '$database' exists...${NC}"
        if check_database_exists "$host" "$port" "$user" "$password" "$database"; then
            echo -e "${GREEN}✓ Database connection successful! Database '$database' exists.${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database connection successful, but database '$database' does not exist.${NC}"
            return 2
        fi
    else
        echo -e "${YELLOW}  Step 2: Testing database access...${NC}"
        local db_test
        if [ -n "$timeout_cmd" ]; then
            db_test=$($timeout_cmd mysql --defaults-file="$temp_config" --connect-timeout=10 -e "USE \`$database\`; SELECT 'OK' AS status;" 2>&1)
        else
            db_test=$(mysql --defaults-file="$temp_config" --connect-timeout=10 -e "USE \`$database\`; SELECT 'OK' AS status;" 2>&1)
        fi
        local db_result=$?
        
        if [ $db_result -eq 124 ]; then
            echo -e "${YELLOW}⚠ Database access test timed out.${NC}"
            return 2
        elif [ $db_result -eq 0 ]; then
            echo -e "${GREEN}✓ Database connection successful! Database '$database' is accessible.${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database connection successful, but cannot access database '$database'.${NC}"
            return 2
        fi
    fi
    
    cleanup_mysql_config "$temp_config"
}

# Function to create database and user
create_database() {
    local host="$1"
    local port="$2"
    local root_password="$3"
    local db_password="$4"
    
    echo -e "${YELLOW}Creating database and user...${NC}"
    echo -e "${BLUE}  Target host: $host:$port${NC}"
    echo -e "${BLUE}  Database: $DB_NAME${NC}"
    echo -e "${BLUE}  User: $DB_USER${NC}"
    
    # Create temporary config file for root user
    local root_temp_config
    root_temp_config=$(mktemp -t mysql_root_config_XXXXXX.cnf)
    
    # Set up cleanup trap
    trap "cleanup_mysql_config '$root_temp_config'" RETURN EXIT INT TERM
    
    # Create root config file
    create_mysql_config_file "root" "$root_password" "$host" "$port" "$root_temp_config"
    
    local root_test
    root_test=$(mysql --defaults-file="$root_temp_config" -e "SELECT 1;" 2>&1)
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Cannot connect as root user!${NC}"
        if echo "$root_test" | grep -q "Access denied"; then
            echo -e "${RED}  Error: Root password is incorrect${NC}"
        fi
        cleanup_mysql_config "$root_temp_config"
        return 1
    fi
    
    echo -e "${GREEN}✓ Root connection successful${NC}"
    
    # Check if database already exists
    if check_database_exists "$host" "$port" "root" "$root_password" "$DB_NAME"; then
        echo -e "${YELLOW}Database '$DB_NAME' already exists.${NC}"
    fi
    
    # Create database and user
    local create_output
    create_output=$(mysql --defaults-file="$root_temp_config" 2>&1 << EOF
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$db_password';
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$db_password';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
EOF
)
    local create_result=$?
    
    # Clean up root config file
    cleanup_mysql_config "$root_temp_config"
    
    if [ $create_result -eq 0 ]; then
        echo -e "${GREEN}✓ Database and user created/updated successfully!${NC}"
        
        # Verify the setup
        echo -e "${YELLOW}Verifying new user connection...${NC}"
        if test_db_connection "$host" "$port" "$DB_USER" "$db_password" "$DB_NAME" "false"; then
            echo -e "${GREEN}✓ User verification successful!${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database created but user verification failed.${NC}"
            return 0
        fi
    else
        echo -e "${RED}✗ Failed to create database and user!${NC}"
        return 1
    fi
}

# Function to update application.properties
update_application_properties() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local database="$5"
    
    echo -e "${YELLOW}Updating application.properties...${NC}"
    echo -e "${BLUE}  Host: $host:$port${NC}"
    echo -e "${BLUE}  Database: $database${NC}"
    echo -e "${BLUE}  User: $user${NC}"
    
    # Create backup
    cp "$APPLICATION_PROPS" "$APPLICATION_PROPS.backup.$(date +%Y%m%d_%H%M%S)"
    
    # Update database configuration
    sed -i.tmp \
        -e "s|^spring\.datasource\.url=.*|spring.datasource.url=jdbc:mariadb://$host:$port/$database|" \
        -e "s|^spring\.datasource\.username=.*|spring.datasource.username=$user|" \
        -e "s|^spring\.datasource\.password=.*|spring.datasource.password=$password|" \
        "$APPLICATION_PROPS"
    
    # Remove temporary file
    rm -f "$APPLICATION_PROPS.tmp"
    
    echo -e "${GREEN}✓ Application properties updated!${NC}"
}

# Function to install MariaDB locally
install_local_mariadb() {
    echo -e "${YELLOW}Installing MariaDB locally...${NC}"
    
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux installation
        if command -v apt-get &> /dev/null; then
            # Debian/Ubuntu
            apt-get update
            apt-get install -y mariadb-server mariadb-client
        elif command -v yum &> /dev/null; then
            # RHEL/CentOS
            yum install -y mariadb-server mariadb
        elif command -v dnf &> /dev/null; then
            # Fedora
            dnf install -y mariadb-server mariadb
        else
            echo -e "${RED}Unsupported Linux distribution. Please install MariaDB manually.${NC}"
            return 1
        fi
        
        # Start and enable MariaDB
        systemctl start mariadb
        systemctl enable mariadb
        
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS installation
        if command -v brew &> /dev/null; then
            brew install mariadb
            brew services start mariadb
        else
            echo -e "${RED}Homebrew not found. Please install MariaDB manually.${NC}"
            return 1
        fi
    else
        echo -e "${RED}Unsupported operating system. Please install MariaDB manually.${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ MariaDB installed successfully!${NC}"
}

# Function to setup H2 database (fallback)
setup_h2_fallback() {
    echo -e "${YELLOW}Setting up H2 in-memory database as fallback...${NC}"
    
    # Create backup
    cp "$APPLICATION_PROPS" "$APPLICATION_PROPS.backup.$(date +%Y%m%d_%H%M%S)"
    
    # Update to H2 configuration
    sed -i.tmp \
        -e "s|^spring\.datasource\.url=.*|spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE|" \
        -e "s|^spring\.datasource\.username=.*|spring.datasource.username=sa|" \
        -e "s|^spring\.datasource\.password=.*|spring.datasource.password=|" \
        -e "s|^spring\.datasource\.driver-class-name=.*|spring.datasource.driver-class-name=org.h2.Driver|" \
        "$APPLICATION_PROPS"
    
    # Add H2 console configuration
    echo "" >> "$APPLICATION_PROPS"
    echo "# H2 Database Configuration" >> "$APPLICATION_PROPS"
    echo "spring.h2.console.enabled=true" >> "$APPLICATION_PROPS"
    echo "spring.h2.console.path=/h2-console" >> "$APPLICATION_PROPS"
    echo "spring.h2.console.settings.web-allow-others=false" >> "$APPLICATION_PROPS"
    
    # Remove temporary file
    rm -f "$APPLICATION_PROPS.tmp"
    
    # Add H2 dependency to build.gradle.kts if not present
    if ! grep -q "com.h2database:h2" app/build.gradle.kts; then
        echo -e "${YELLOW}Adding H2 dependency to build.gradle.kts...${NC}"
        sed -i.tmp '/implementation("org.mariadb.jdbc:mariadb-java-client/a\    runtimeOnly("com.h2database:h2") // H2 Database for development/testing' app/build.gradle.kts
        rm -f app/build.gradle.kts.tmp
    fi
    
    echo -e "${GREEN}✓ H2 database configured as fallback!${NC}"
}

# Main database setup function
setup_database() {
    echo -e "${BLUE}Database Setup Options:${NC}"
    echo "1. Connect to existing MariaDB/MySQL database"
    echo "2. Install and setup MariaDB locally"
    echo "3. Use H2 in-memory database (development only)"
    echo
    read -p "Please choose an option (1-3): " choice
    
    case $choice in
        1)
            echo -e "${YELLOW}Connecting to existing database...${NC}"
            
            # Pre-flight check: MySQL client
            echo -e "${BLUE}Checking MySQL client availability...${NC}"
            if ! check_mysql_client_installed; then
                echo -e "${RED}MySQL client is required but not found!${NC}"
                echo -e "${YELLOW}Using H2 fallback database.${NC}"
                setup_h2_fallback
                return 0
            fi
            
            # Test basic MySQL client functionality
            echo -e "${BLUE}Testing MySQL client basic functionality...${NC}"
            if ! mysql --help >/dev/null 2>&1; then
                echo -e "${RED}✗ MySQL client appears to be broken${NC}"
                setup_h2_fallback
                return 0
            fi
            echo -e "${GREEN}✓ MySQL client is working${NC}"
            
            local max_retries=3
            local retry_count=0
            local connection_successful=false
            
            while [ $retry_count -lt $max_retries ] && [ "$connection_successful" = false ]; do
                if [ $retry_count -gt 0 ]; then
                    echo -e "${YELLOW}\nRetry attempt $retry_count of $((max_retries-1))...${NC}"
                fi
                
                read -p "Database host (default: localhost): " db_host
                db_host=${db_host:-localhost}
                
                read -p "Database port (default: 3306): " db_port
                db_port=${db_port:-3306}
                
                read -p "Database name (default: $DB_NAME): " db_name
                db_name=${db_name:-$DB_NAME}
                
                read -p "Database user (default: $DB_USER): " db_user
                db_user=${db_user:-$DB_USER}
                
                read -s -p "Database password: " db_password
                echo
                
                echo -e "${BLUE}Attempting connection with:${NC}"
                echo -e "${BLUE}  Host: $db_host${NC}"
                echo -e "${BLUE}  Port: $db_port${NC}"
                echo -e "${BLUE}  User: $db_user${NC}"
                echo -e "${BLUE}  Database: $db_name${NC}"
                echo
                
                if ! test_mysql_server_reachable "$db_host" "$db_port"; then
                    retry_count=$((retry_count + 1))
                    continue
                fi
                
                local conn_result
                test_db_connection "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
                conn_result=$?
                
                if [ $conn_result -eq 0 ]; then
                    update_application_properties "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
                    connection_successful=true
                    echo -e "${GREEN}✓ Using verified database credentials${NC}"
                elif [ $conn_result -eq 2 ]; then
                    echo -e "${YELLOW}\nDatabase connection works, but database is not accessible.${NC}"
                    echo -e "${YELLOW}Options:${NC}"
                    echo "1. Create the database (requires root access)"
                    echo "2. Try different database credentials"
                    echo "3. Use H2 fallback database"
                    read -p "Choose option (1-3): " db_option
                    
                    case $db_option in
                        1)
                            read -s -p "Enter MySQL root password: " root_password
                            echo
                            if create_database "$db_host" "$db_port" "$root_password" "$db_password"; then
                                update_application_properties "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
                                connection_successful=true
                            else
                                retry_count=$((retry_count + 1))
                            fi
                            ;;
                        2)
                            retry_count=$((retry_count + 1))
                            ;;
                        3)
                            setup_h2_fallback
                            connection_successful=true
                            ;;
                        *)
                            retry_count=$((retry_count + 1))
                            ;;
                    esac
                else
                    retry_count=$((retry_count + 1))
                fi
            done
            
            if [ "$connection_successful" = false ]; then
                echo -e "${RED}\nMaximum retry attempts reached.${NC}"
                echo -e "${YELLOW}Using H2 fallback database.${NC}"
                setup_h2_fallback
            fi
            ;;
        2)
            echo -e "${YELLOW}Installing MariaDB locally...${NC}"
            
            if install_local_mariadb; then
                read -s -p "Set password for database user '$DB_USER': " db_password
                echo
                
                if create_database "localhost" "$MARIADB_PORT" "" "$db_password"; then
                    update_application_properties "localhost" "$MARIADB_PORT" "$DB_USER" "$db_password" "$DB_NAME"
                else
                    echo -e "${YELLOW}Initial setup failed. Using H2 fallback.${NC}"
                    setup_h2_fallback
                fi
            else
                echo -e "${YELLOW}MariaDB installation failed. Using H2 fallback.${NC}"
                setup_h2_fallback
            fi
            ;;
        3)
            setup_h2_fallback
            ;;
        *)
            echo -e "${RED}Invalid choice. Using H2 fallback.${NC}"
            setup_h2_fallback
            ;;
    esac
}

# Function to configure application.properties for production
configure_production_properties() {
    echo -e "${YELLOW}Configuring application.properties for production...${NC}"
    
    if [ ! -f "$APPLICATION_PROPS" ]; then
        echo -e "${RED}✗ application.properties not found at $APPLICATION_PROPS${NC}"
        return 1
    fi
    
    # Create backup
    local backup_file="$APPLICATION_PROPS.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$APPLICATION_PROPS" "$backup_file"
    echo -e "${BLUE}  Backup created: $backup_file${NC}"
    
    # Remove the admin user line and add production flag
    local temp_file
    temp_file=$(mktemp)
    
    if grep -v '^users\.admin=' "$APPLICATION_PROPS" > "$temp_file"; then
        # Add production flag at the end
        echo "" >> "$temp_file"
        echo "# Production Mode Flag" >> "$temp_file"
        echo "app.production=true" >> "$temp_file"
        
        mv "$temp_file" "$APPLICATION_PROPS"
        echo -e "${GREEN}✓ Admin user removed and production mode enabled${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to configure production properties${NC}"
        rm -f "$temp_file"
        return 1
    fi
}

# Function to build the application
build_application() {
    echo -e "${BLUE}Building the application...${NC}"
    
    # Make gradlew executable on Linux/macOS
    if [[ "$OSTYPE" == "linux-gnu"* ]] || [[ "$OSTYPE" == "darwin"* ]]; then
        echo -e "${YELLOW}Setting execute permissions on gradlew...${NC}"
        chmod +x ./gradlew
        if [ -x "./gradlew" ]; then
            echo -e "${GREEN}✓ gradlew is now executable${NC}"
        else
            echo -e "${RED}✗ Failed to make gradlew executable${NC}"
            return 1
        fi
    fi
    
    echo -e "${YELLOW}Running Gradle build...${NC}"
    ./gradlew build
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Application built successfully!${NC}"
        return 0
    else
        echo -e "${RED}✗ Build failed!${NC}"
        return 1
    fi
}

# Function to copy JAR to target directory and restart service
deploy_jar() {
    local target_dir="$1"
    local service_name="$2"
    
    # Read version from version.txt
    local app_version
    if [ ! -f "version.txt" ]; then
        echo -e "${RED}✗ version.txt not found in project root${NC}"
        return 1
    fi
    app_version=$(cat version.txt | tr -d '[:space:]')
    
    # Find the JAR file matching the version
    local jar_file
    jar_file=$(find app/build/libs -name "*-${app_version}.jar" -type f 2>/dev/null | head -1)
    
    if [ -z "$jar_file" ]; then
        echo -e "${RED}✗ No JAR file found matching version $app_version in app/build/libs${NC}"
        echo -e "${YELLOW}Available JAR files:${NC}"
        find app/build/libs -name "*.jar" -type f 2>/dev/null | sed 's/^/  /'
        return 1
    fi
    
    echo -e "${BLUE}Using JAR: $jar_file (version $app_version)${NC}"
    
    echo -e "${YELLOW}Stopping service '$service_name'...${NC}"
    if systemctl stop "$service_name" 2>/dev/null; then
        echo -e "${GREEN}✓ Service stopped${NC}"
        # Wait for service to fully stop
        sleep 2
    else
        echo -e "${YELLOW}⚠ Could not stop service (may not be running)${NC}"
    fi
    
    echo -e "${YELLOW}Copying JAR to $target_dir...${NC}"
    if ! mkdir -p "$target_dir"; then
        echo -e "${RED}✗ Failed to create target directory${NC}"
        return 1
    fi
    
    if cp "$jar_file" "$target_dir/app.jar"; then
        echo -e "${GREEN}✓ JAR copied successfully to $target_dir/app.jar${NC}"
    else
        echo -e "${RED}✗ Failed to copy JAR${NC}"
        return 1
    fi
    
    echo -e "${YELLOW}Starting service '$service_name'...${NC}"
    if systemctl start "$service_name" 2>/dev/null; then
        echo -e "${GREEN}✓ Service started${NC}"
    else
        echo -e "${YELLOW}⚠ Could not start service (may need manual intervention)${NC}"
    fi
    
    return 0
}

# Main execution
main() {
    set +e
    
    # Check sudo
    check_sudo
    
    # Check if we're in the right directory
    if [ ! -f "settings.gradle.kts" ]; then
        echo -e "${RED}Error: This script must be run from the project root directory!${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Theia01 Governance Tool Build Setup  ${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo
    
    local use_saved_config=false
    
    # Try to load saved configuration
    if load_saved_config; then
        echo -e "${YELLOW}Use saved values? (y/n): ${NC}"
        read -p "" use_saved
        if [[ $use_saved =~ ^[Yy]$ ]]; then
            use_saved_config=true
        else
            unset DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD TARGET_DIRECTORY SERVICE_NAME
        fi
    fi
    
    echo
    echo -e "${BLUE}Step 1: Database Setup${NC}"
    
    if [ "$use_saved_config" = false ]; then
        setup_database
    else
        echo -e "${GREEN}✓ Using saved database configuration${NC}"
        db_host="$DB_HOST"
        db_port="$DB_PORT"
        db_name="$DB_NAME"
        db_user="$DB_USER"
        db_password="$DB_PASSWORD"
        update_application_properties "$DB_HOST" "$DB_PORT" "$DB_USER" "$DB_PASSWORD" "$DB_NAME"
    fi
    
    echo
    echo -e "${BLUE}Step 2: Deployment Type Selection${NC}"
    
    echo -e "${BLUE}Select deployment type:${NC}"
    echo "1. Test"
    echo "2. Production"
    echo
    read -p "Please choose deployment type (1-2): " deployment_type
    
    case $deployment_type in
        1)
            echo -e "${GREEN}✓ Test deployment selected${NC}"
            deployment_type="test"
            ;;
        2)
            echo -e "${GREEN}✓ Production deployment selected${NC}"
            deployment_type="prod"
            ;;
        *)
            echo -e "${RED}Invalid choice. Defaulting to test deployment.${NC}"
            deployment_type="test"
            ;;
    esac
    
    echo
    echo -e "${BLUE}Step 3: Building Application${NC}"
    
    if build_application; then
        # For production deployments, configure production properties
        if [ "$deployment_type" = "prod" ]; then
            echo
            if ! configure_production_properties; then
                echo -e "${RED}Failed to configure production properties${NC}"
                return 1
            fi
        fi
        
        echo
        echo -e "${BLUE}Step 4: Deployment Configuration${NC}"
        
        local target_dir
        local service_name
        
        if [ "$use_saved_config" = true ]; then
            target_dir="$TARGET_DIRECTORY"
            service_name="$SERVICE_NAME"
            echo -e "${GREEN}✓ Using saved deployment configuration${NC}"
            echo -e "${BLUE}Target directory: $target_dir${NC}"
            echo -e "${BLUE}Service name: $service_name${NC}"
        else
            read -p "Enter target directory for JAR deployment: " target_dir
            if [ -z "$target_dir" ]; then
                echo -e "${RED}Target directory cannot be empty${NC}"
                return 1
            fi
            
            read -p "Enter service name to stop/start during deployment: " service_name
            if [ -z "$service_name" ]; then
                echo -e "${RED}Service name cannot be empty${NC}"
                return 1
            fi
        fi
        
        # Save configuration for future use (only if not using pre-saved config)
        if [ "$use_saved_config" = false ]; then
            echo
            echo -e "${YELLOW}Saving configuration for future use...${NC}"
            save_config "$db_host" "$db_port" "$db_name" "$db_user" "$db_password" "$target_dir" "$service_name"
        fi
        
        echo
        echo -e "${BLUE}Step 5: Deploying JAR${NC}"
        
        if deploy_jar "$target_dir" "$service_name"; then
            echo
            echo -e "${GREEN}========================================${NC}"
            echo -e "${GREEN}  Build Setup Completed Successfully!  ${NC}"
            echo -e "${GREEN}========================================${NC}"
            echo
            echo -e "${BLUE}JAR deployed to: $target_dir/app.jar${NC}"
            echo -e "${BLUE}Service name: $service_name${NC}"
            echo
            echo -e "${GREEN}✓ Setup completed successfully!${NC}"
            return 0
        else
            echo
            echo -e "${YELLOW}⚠ Build successful but deployment failed${NC}"
            echo -e "${YELLOW}JAR file location: app/build/libs${NC}"
            return 1
        fi
    else
        echo
        echo -e "${RED}Build failed! Please check the errors above.${NC}"
        return 1
    fi
}

# Ensure this script is executable
chmod +x "$0" 2>/dev/null || true

# Set up signal handling
trap 'echo -e "\n${YELLOW}Script interrupted by user${NC}"; exit 130' INT TERM

# Run main function
if main "$@"; then
    echo -e "${GREEN}Script completed successfully!${NC}"
    exit 0
else
    echo -e "${RED}Script completed with errors.${NC}"
    exit 1
fi
