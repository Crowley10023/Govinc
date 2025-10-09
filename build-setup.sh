#!/bin/bash

# Build Setup Script for Theia01 Governance Tool
# This script handles database setup and application build

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

# Function to test database connection
test_db_connection() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local database="$5"
    
    echo -e "${YELLOW}Testing database connection...${NC}"
    
    if mysql -h"$host" -P"$port" -u"$user" -p"$password" -e "USE $database;" 2>/dev/null; then
        echo -e "${GREEN}✓ Database connection successful!${NC}"
        return 0
    else
        echo -e "${RED}✗ Database connection failed!${NC}"
        return 1
    fi
}

# Function to create database and user
create_database() {
    local host="$1"
    local port="$2"
    local root_password="$3"
    local db_password="$4"
    
    echo -e "${YELLOW}Creating database and user...${NC}"
    
    mysql -h"$host" -P"$port" -uroot -p"$root_password" << EOF
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$db_password';
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$db_password';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
EOF
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Database and user created successfully!${NC}"
        return 0
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
            
            if test_db_connection "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"; then
                update_application_properties "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
            else
                echo -e "${RED}Would you like to create the database? (y/n)${NC}"
                read -p "" create_db
                if [[ $create_db =~ ^[Yy]$ ]]; then
                    read -s -p "Enter MySQL root password: " root_password
                    echo
                    if create_database "$db_host" "$db_port" "$root_password" "$db_password"; then
                        update_application_properties "$db_host" "$db_port" "$db_user" "$db_password" "$db_name"
                    else
                        echo -e "${RED}Failed to create database. Using H2 fallback.${NC}"
                        setup_h2_fallback
                    fi
                else
                    echo -e "${YELLOW}Using H2 fallback database.${NC}"
                    setup_h2_fallback
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

# Main execution
main() {
    # Check if we're in the right directory
    if [ ! -f "settings.gradle.kts" ]; then
        echo -e "${RED}Error: This script must be run from the project root directory!${NC}"
        exit 1
    fi
    
    # Setup database
    setup_database
    
    echo
    echo -e "${BLUE}Building application...${NC}"
    
    # Build application
    if build_application; then
        echo
        echo -e "${YELLOW}Would you like to run tests? (y/n)${NC}"
        read -p "" run_test
        if [[ $run_test =~ ^[Yy]$ ]]; then
            run_tests
        fi
        
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
        if grep -q "h2:mem" "$APPLICATION_PROPS"; then
            echo -e "${YELLOW}Database: H2 in-memory (development mode)${NC}"
            echo -e "${YELLOW}H2 Console: http://localhost:8080/h2-console${NC}"
        else
            db_url=$(grep "^spring.datasource.url=" "$APPLICATION_PROPS" | cut -d'=' -f2-)
            echo -e "${YELLOW}Database: $db_url${NC}"
        fi
    else
        echo -e "${RED}Build failed! Please check the errors above.${NC}"
        exit 1
    fi
}

# Run main function
main "$@"