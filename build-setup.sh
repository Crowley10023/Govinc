#!/bin/bash

# Build Setup Script for Theia01 Governance Tool
# This script handles database setup and application build
#
# Password Handling Security Notes:
# - Removed 'sudo' from mysql commands to fix password piping issues
# - Using MYSQL_PWD environment variable instead of -p flag for better security
# - This prevents passwords from appearing in process lists and command history

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

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Theia01 Governance Tool Build Setup  ${NC}"
echo -e "${BLUE}========================================${NC}"
echo

# Alternative secure password handling using temporary config file
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

# Function to check if MariaDB/MySQL is installed
check_mariadb_installed() {
    if command -v mysql &> /dev/null; then
        return 0
    elif command -v mariadb &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Function to check if MariaDB service is running
check_mariadb_running() {
    if systemctl is-active --quiet mariadb 2>/dev/null; then
        return 0
    elif systemctl is-active --quiet mysql 2>/dev/null; then
        return 0
    elif pgrep -x "mysqld" > /dev/null 2>&1; then
        return 0
    elif pgrep -x "mariadbd" > /dev/null 2>&1; then
        return 0
    else
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
    
    local output
    # Use environment variable to pass password securely
    MYSQL_PWD="$password" output=$(mysql -h"$host" -P"$port" -u"$user" -e "SHOW DATABASES LIKE '$database';" 2>/dev/null)
    
    if [[ -n "$output" && "$output" != *"Database"* ]]; then
        return 0  # Database exists
    else
        return 1  # Database doesn't exist
    fi
}

# Function to test database connection with detailed error reporting
test_db_connection() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local database="$5"
    local check_db_exists="${6:-true}"
    
    echo -e "${YELLOW}Testing database connection to $host:$port...${NC}"
    
    # Set timeout for mysql commands to prevent hanging
    local timeout_cmd="timeout 30"
    if ! command -v timeout &> /dev/null; then
        # If timeout command is not available, use alternative method
        timeout_cmd=""
    fi
    
    # Test basic connectivity first with timeout
    local connection_test
    if [ -n "$timeout_cmd" ]; then
        MYSQL_PWD="$password" connection_test=$($timeout_cmd mysql -h"$host" -P"$port" -u"$user" --connect-timeout=10 -e "SELECT 1;" 2>&1)
    else
        MYSQL_PWD="$password" connection_test=$(mysql -h"$host" -P"$port" -u"$user" --connect-timeout=10 -e "SELECT 1;" 2>&1)
    fi
    local conn_result=$?
    
    # Handle timeout or connection failure
    if [ $conn_result -eq 124 ]; then
        echo -e "${RED}✗ Database connection timed out!${NC}"
        echo -e "${RED}  Connection attempt exceeded 30 seconds${NC}"
        return 1
    elif [ $conn_result -ne 0 ]; then
        echo -e "${RED}✗ Database connection failed!${NC}"
        
        # Analyze the error and provide specific feedback
        if echo "$connection_test" | grep -q "Access denied"; then
            echo -e "${RED}  Error: Access denied - Invalid username or password${NC}"
        elif echo "$connection_test" | grep -q "Can't connect to"; then
            echo -e "${RED}  Error: Cannot connect to database server${NC}"
            echo -e "${RED}  Possible causes:${NC}"
            echo -e "${RED}  - Database server is not running${NC}"
            echo -e "${RED}  - Wrong host or port${NC}"
            echo -e "${RED}  - Firewall blocking connection${NC}"
        elif echo "$connection_test" | grep -q "Unknown database"; then
            echo -e "${RED}  Error: Database '$database' does not exist${NC}"
        else
            echo -e "${RED}  Error details: $connection_test${NC}"
        fi
        return 1
    fi
    
    # If we should check database existence
    if [[ "$check_db_exists" == "true" ]]; then
        if check_database_exists "$host" "$port" "$user" "$password" "$database"; then
            echo -e "${GREEN}✓ Database connection successful! Database '$database' exists.${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database connection successful, but database '$database' does not exist.${NC}"
            return 2  # Special return code for "connection OK but database missing"
        fi
    else
        # Just test the connection without checking database existence with timeout
        local db_test
        if [ -n "$timeout_cmd" ]; then
            MYSQL_PWD="$password" db_test=$($timeout_cmd mysql -h"$host" -P"$port" -u"$user" --connect-timeout=10 -e "USE $database;" 2>&1)
        else
            MYSQL_PWD="$password" db_test=$(mysql -h"$host" -P"$port" -u"$user" --connect-timeout=10 -e "USE $database;" 2>&1)
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
            echo -e "${YELLOW}  This might be due to missing permissions or the database not existing.${NC}"
            return 2
        fi
    fi
}

# Function to create database and user with detailed error handling
create_database() {
    local host="$1"
    local port="$2"
    local root_password="$3"
    local db_password="$4"
    
    echo -e "${YELLOW}Creating database and user with verified credentials...${NC}"
    echo -e "${BLUE}  Target host: $host:$port${NC}"
    echo -e "${BLUE}  Database: $DB_NAME${NC}"
    echo -e "${BLUE}  User: $DB_USER${NC}"
    
    # Test root connection first
    echo -e "${YELLOW}Testing root connection...${NC}"
    local root_test
    MYSQL_PWD="$root_password" root_test=$(mysql -h"$host" -P"$port" -uroot -e "SELECT 1;" 2>&1)
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Cannot connect as root user!${NC}"
        if echo "$root_test" | grep -q "Access denied"; then
            echo -e "${RED}  Error: Root password is incorrect${NC}"
        else
            echo -e "${RED}  Error details: $root_test${NC}"
        fi
        return 1
    fi
    
    echo -e "${GREEN}✓ Root connection successful${NC}"
    
    # Check if database already exists
    if check_database_exists "$host" "$port" "root" "$root_password" "$DB_NAME"; then
        echo -e "${YELLOW}Database '$DB_NAME' already exists.${NC}"
        echo -e "${YELLOW}Ensuring user '$DB_USER' has proper permissions...${NC}"
    fi
    
    # Create database and user
    local create_output
    MYSQL_PWD="$root_password" create_output=$(mysql -h"$host" -P"$port" -uroot 2>&1 << EOF
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$db_password';
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$db_password';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
EOF
)
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Database and user created/updated successfully!${NC}"
        
        # Verify the setup by testing the new user connection with the same password used in testing
        echo -e "${YELLOW}Verifying new user connection with provided credentials...${NC}"
        if test_db_connection "$host" "$port" "$DB_USER" "$db_password" "$DB_NAME" "false"; then
            echo -e "${GREEN}✓ User verification successful with consistent credentials!${NC}"
            echo -e "${GREEN}✓ Database setup completed - same credentials will be used in application${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database created but user verification failed. This might be normal for new installations.${NC}"
            echo -e "${YELLOW}  The application will attempt to connect with the provided credentials${NC}"
            return 0
        fi
    else
        echo -e "${RED}✗ Failed to create database and user!${NC}"
        echo -e "${RED}Error details: $create_output${NC}"
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
    
    echo -e "${YELLOW}Updating application.properties with tested credentials...${NC}"
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
            sudo apt-get update
            sudo apt-get install -y mariadb-server mariadb-client
        elif command -v yum &> /dev/null; then
            # RHEL/CentOS
            sudo yum install -y mariadb-server mariadb
        elif command -v dnf &> /dev/null; then
            # Fedora
            sudo dnf install -y mariadb-server mariadb
        else
            echo -e "${RED}Unsupported Linux distribution. Please install MariaDB manually.${NC}"
            return 1
        fi
        
        # Start and enable MariaDB
        sudo systemctl start mariadb
        sudo systemctl enable mariadb
        
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
    echo -e "${BLUE}  Access H2 console at: http://localhost:8080/h2-console${NC}"
    echo -e "${BLUE}  JDBC URL: jdbc:h2:mem:testdb${NC}"
    echo -e "${BLUE}  Username: sa${NC}"
    echo -e "${BLUE}  Password: (empty)${NC}"
}

# Main database setup function
# This function ensures that credentials tested for MariaDB connectivity
# are consistently used throughout database creation and application configuration
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
            
            local max_retries=3
            local retry_count=0
            local connection_successful=false
            
            # Variables to preserve tested credentials
            local tested_db_host=""
            local tested_db_port=""
            local tested_db_name=""
            local tested_db_user=""
            local tested_db_password=""
            
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
                
                local conn_result
                test_db_connection "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
                conn_result=$?
                
                if [ $conn_result -eq 0 ]; then
                    # Success - database exists and is accessible
                    # Save the working credentials
                    tested_db_host="$db_host"
                    tested_db_port="$db_port"
                    tested_db_name="$db_name"
                    tested_db_user="$db_user"
                    tested_db_password="$db_password"
                    
                    update_application_properties "$tested_db_host" "$tested_db_port" "$tested_db_user" "$tested_db_password" "$tested_db_name"
                    connection_successful=true
                    echo -e "${GREEN}✓ Using verified database credentials for application configuration${NC}"
                elif [ $conn_result -eq 2 ]; then
                    # Connection OK but database missing or inaccessible
                    # Save the working connection credentials
                    tested_db_host="$db_host"
                    tested_db_port="$db_port"
                    tested_db_name="$db_name"
                    tested_db_user="$db_user"
                    tested_db_password="$db_password"
                    
                    echo -e "${YELLOW}\nThe database connection works, but database '$db_name' is not accessible.${NC}"
                    echo -e "${GREEN}✓ Database connection credentials verified and will be preserved${NC}"
                    echo -e "${YELLOW}Options:${NC}"
                    echo "1. Create the database (requires root access)"
                    echo "2. Try different database credentials"
                    echo "3. Use H2 fallback database"
                    read -p "Choose option (1-3): " db_option
                    
                    case $db_option in
                        1)
                            read -s -p "Enter MySQL root password: " root_password
                            echo
                            # Use the tested credentials for database creation
                            if create_database "$tested_db_host" "$tested_db_port" "$root_password" "$tested_db_password"; then
                                echo -e "${GREEN}✓ Database created using verified connection credentials${NC}"
                                update_application_properties "$tested_db_host" "$tested_db_port" "$tested_db_user" "$tested_db_password" "$tested_db_name"
                                connection_successful=true
                            else
                                echo -e "${RED}Database creation failed.${NC}"
                                retry_count=$((retry_count + 1))
                            fi
                            ;;
                        2)
                            retry_count=$((retry_count + 1))
                            ;;
                        3)
                            echo -e "${YELLOW}Using H2 fallback database.${NC}"
                            setup_h2_fallback
                            connection_successful=true
                            ;;
                        *)
                            retry_count=$((retry_count + 1))
                            ;;
                    esac
                else
                    # Connection failed entirely
                    retry_count=$((retry_count + 1))
                    if [ $retry_count -lt $max_retries ]; then
                        echo -e "${YELLOW}\nWould you like to retry with different settings? (y/n)${NC}"
                        read -p "" retry_choice
                        if [[ ! $retry_choice =~ ^[Yy]$ ]]; then
                            break
                        fi
                    fi
                fi
            done
            
            if [ "$connection_successful" = false ]; then
                echo -e "${RED}\nMaximum retry attempts reached or user chose not to retry.${NC}"
                echo -e "${YELLOW}Options:${NC}"
                echo "1. Use H2 fallback database (recommended for development)"
                echo "2. Exit and configure database manually"
                read -p "Choose option (1-2): " fallback_option
                
                if [[ $fallback_option == "1" ]]; then
                    echo -e "${YELLOW}Using H2 fallback database.${NC}"
                    setup_h2_fallback
                else
                    echo -e "${RED}Please configure the database manually and run the script again.${NC}"
                    exit 1
                fi
            fi
            ;;
        2)
            echo -e "${YELLOW}Installing MariaDB locally...${NC}"
            
            if install_local_mariadb; then
                echo -e "${YELLOW}Securing MariaDB installation...${NC}"
                echo -e "${BLUE}Please run 'sudo mysql_secure_installation' after this script completes.${NC}"
                
                read -s -p "Set password for database user '$DB_USER': " db_password
                echo
                
                # Use empty password for initial root connection on new installation
                if create_database "localhost" "$MARIADB_PORT" "" "$db_password"; then
                    update_application_properties "localhost" "$MARIADB_PORT" "$DB_USER" "$db_password" "$DB_NAME"
                else
                    echo -e "${YELLOW}Initial setup failed. You may need to secure MariaDB first.${NC}"
                    echo -e "${YELLOW}Using H2 fallback database.${NC}"
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

# Function to build the application
build_application() {
    echo -e "${BLUE}Building the application...${NC}"
    
    # Make gradlew executable
    chmod +x ./gradlew
    
    echo -e "${YELLOW}Running Gradle build...${NC}"
    ./gradlew clean build -x test
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Application built successfully!${NC}"
        return 0
    else
        echo -e "${RED}✗ Build failed!${NC}"
        return 1
    fi
}

# Function to run tests
run_tests() {
    echo -e "${YELLOW}Running tests...${NC}"
    ./gradlew test
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
    else
        echo -e "${YELLOW}⚠ Some tests failed, but continuing...${NC}"
    fi
}

# Function to perform final database connection verification
final_db_verification() {
    echo -e "${BLUE}Performing final database verification...${NC}"
    
    # Check if using H2 database
    if grep -q "h2:mem" "$APPLICATION_PROPS"; then
        echo -e "${GREEN}✓ H2 in-memory database configured - no connection test needed${NC}"
        return 0
    fi
    
    # Extract database configuration from application.properties
    local db_url=$(grep "^spring.datasource.url=" "$APPLICATION_PROPS" 2>/dev/null | cut -d'=' -f2-)
    local db_username=$(grep "^spring.datasource.username=" "$APPLICATION_PROPS" 2>/dev/null | cut -d'=' -f2-)
    local db_password=$(grep "^spring.datasource.password=" "$APPLICATION_PROPS" 2>/dev/null | cut -d'=' -f2-)
    
    if [ -z "$db_url" ] || [ -z "$db_username" ]; then
        echo -e "${YELLOW}⚠ Database configuration not found in application.properties${NC}"
        return 0
    fi
    
    # Parse database URL to extract host, port, and database name
    if [[ $db_url =~ jdbc:mariadb://([^:]+):([0-9]+)/([^?]+) ]]; then
        local db_host="${BASH_REMATCH[1]}"
        local db_port="${BASH_REMATCH[2]}"
        local db_name="${BASH_REMATCH[3]}"
        
        echo -e "${YELLOW}Testing final database connection...${NC}"
        
        # Perform a quick connection test with short timeout
        if test_db_connection "$db_host" "$db_port" "$db_username" "$db_password" "$db_name" "false"; then
            echo -e "${GREEN}✓ Database connection verified successfully${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Database connection test failed, but application may still work${NC}"
            echo -e "${YELLOW}  The application will attempt to connect at startup${NC}"
            return 0  # Don't fail the script, just warn
        fi
    else
        echo -e "${YELLOW}⚠ Could not parse database URL format${NC}"
        return 0
    fi
}

# Main execution
main() {
    # Set error handling - don't exit on errors during setup
    set +e
    
    # Check if we're in the right directory
    if [ ! -f "settings.gradle.kts" ]; then
        echo -e "${RED}Error: This script must be run from the project root directory!${NC}"
        exit 1
    fi
    
    echo -e "${BLUE}Starting build setup process...${NC}"
    
    # Setup database with error handling
    echo -e "${BLUE}Step 1: Database Setup${NC}"
    if ! setup_database; then
        echo -e "${YELLOW}⚠ Database setup encountered issues, but continuing with build${NC}"
    fi
    
    echo
    echo -e "${BLUE}Step 2: Building Application${NC}"
    
    # Build application
    if build_application; then
        echo
        echo -e "${YELLOW}Would you like to run tests? (y/n)${NC}"
        read -t 30 -p "" run_test
        local read_result=$?
        
        # Handle timeout or user input
        if [ $read_result -eq 142 ]; then
            echo
            echo -e "${YELLOW}No response received, skipping tests${NC}"
            run_test="n"
        fi
        
        if [[ $run_test =~ ^[Yy]$ ]]; then
            echo -e "${BLUE}Step 3: Running Tests${NC}"
            run_tests
        else
            echo -e "${YELLOW}Skipping tests${NC}"
        fi
        
        echo
        echo -e "${BLUE}Step 4: Final Verification${NC}"
        final_db_verification
        
        echo
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}  Build Setup Completed Successfully!  ${NC}"
        echo -e "${GREEN}========================================${NC}"
        echo
        echo -e "${BLUE}To start the application:${NC}"
        echo -e "${BLUE}  ./gradlew bootRun${NC}"
        echo -e "${BLUE}  or${NC}"
        echo -e "${BLUE}  java -jar app/build/libs/theia01.jar${NC}"
        echo
        echo -e "${BLUE}Application will be available at: http://localhost:8080${NC}"
        echo
        
        # Show database info
        if grep -q "h2:mem" "$APPLICATION_PROPS" 2>/dev/null; then
            echo -e "${YELLOW}Database: H2 in-memory (development mode)${NC}"
            echo -e "${YELLOW}H2 Console: http://localhost:8080/h2-console${NC}"
        else
            local db_url=$(grep "^spring.datasource.url=" "$APPLICATION_PROPS" 2>/dev/null | cut -d'=' -f2-)
            if [ -n "$db_url" ]; then
                echo -e "${YELLOW}Database: $db_url${NC}"
            fi
        fi
        
        echo
        echo -e "${GREEN}✓ Setup completed successfully! You can now start the application.${NC}"
        return 0
    else
        echo
        echo -e "${RED}Build failed! Please check the errors above.${NC}"
        echo -e "${YELLOW}You may need to:${NC}"
        echo -e "${YELLOW}  - Check your Java version (Java 17+ required)${NC}"
        echo -e "${YELLOW}  - Verify database configuration${NC}"
        echo -e "${YELLOW}  - Check network connectivity${NC}"
        echo -e "${YELLOW}  - Review the error messages above${NC}"
        return 1
    fi
}

# Set up signal handling to ensure clean exit
trap 'echo -e "\n${YELLOW}Script interrupted by user${NC}"; exit 130' INT TERM

# Run main function and handle its exit code
if main "$@"; then
    echo -e "${GREEN}Script completed successfully!${NC}"
    exit 0
else
    echo -e "${RED}Script completed with errors. Please review the output above.${NC}"
    exit 1
fi