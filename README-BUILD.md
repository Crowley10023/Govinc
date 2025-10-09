# Theia01 Governance Tool - Build Setup Guide

This document explains how to set up and build the Theia01 Governance Tool with database configuration.

## Quick Start

### Linux/macOS
```bash
chmod +x build-setup.sh
./build-setup.sh
```

### Windows
```cmd
build-setup.bat
```

## What the Build Script Does

The build setup script automates the entire setup process:

1. **Database Configuration**
   - Connects to existing MariaDB/MySQL database
   - Creates local MariaDB installation (Linux/macOS)
   - Falls back to H2 in-memory database for development

2. **Application Configuration**
   - Updates `application.properties` with database settings
   - Creates backup of existing configuration

3. **Build Process**
   - Runs Gradle clean build
   - Optionally runs tests
   - Generates executable JAR

## Database Setup Options

### Option 1: Existing MariaDB/MySQL Database
- Connect to an existing database server
- Create database and user if they don't exist
- Requires database connection details

### Option 2: Local MariaDB Installation (Linux/macOS only)
- Automatically installs MariaDB on the local system
- Sets up database and user
- Requires sudo privileges

### Option 3: H2 In-Memory Database (Development)
- No installation required
- Data is not persistent (lost on restart)
- Perfect for development and testing
- Access H2 console at: http://localhost:8080/h2-console

## Manual Database Setup

If you prefer to set up the database manually:

### MariaDB/MySQL Setup
```sql
-- Create database
CREATE DATABASE govinc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user
CREATE USER 'govinc'@'localhost' IDENTIFIED BY 'your_password';
CREATE USER 'govinc'@'%' IDENTIFIED BY 'your_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON govinc.* TO 'govinc'@'localhost';
GRANT ALL PRIVILEGES ON govinc.* TO 'govinc'@'%';
FLUSH PRIVILEGES;
```

### Update Application Properties
Edit `app/src/main/resources/application.properties`:

```properties
# Database settings
spring.datasource.url=jdbc:mariadb://localhost:3306/govinc
spring.datasource.username=govinc
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
```

## Building Without the Script

### Prerequisites
- Java 11 or higher
- Gradle (or use included Gradle wrapper)

### Manual Build Commands

```bash
# Make gradlew executable (Linux/macOS)
chmod +x ./gradlew

# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Run application
./gradlew bootRun

# Or run the JAR directly
java -jar app/build/libs/theia01.jar
```

### Windows
```cmd
gradlew.bat clean build
gradlew.bat test
gradlew.bat bootRun
```

## Running the Application

After successful build, start the application:

### Using Gradle
```bash
./gradlew bootRun
```

### Using JAR
```bash
java -jar app/build/libs/theia01.jar
```

The application will be available at: http://localhost:8080

## Database Access

### MariaDB/MySQL
- Use your configured database credentials
- Default database name: `govinc`
- Default user: `govinc`

### H2 Console (Development Mode)
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

## Authentication

The application includes local authentication with default users:

- **Admin User:**
  - Username: `admin`
  - Password: `admin`
  - Email: `admin@example.com`

- **Test User:**
  - Username: `testuser`
  - Password: `test123`
  - Email: `test@example.com`

Additional users can be configured in `application.properties`:
```properties
users.newuser=password,email@example.com
```

## Troubleshooting

### Build Failures
1. Ensure Java 11+ is installed: `java -version`
2. Check Gradle wrapper permissions: `chmod +x ./gradlew`
3. Clear Gradle cache: `./gradlew clean --refresh-dependencies`

### Database Connection Issues
1. Verify database server is running
2. Check firewall settings
3. Verify database credentials
4. Test connection manually with MySQL client

### Permission Issues (Linux/macOS)
1. Make script executable: `chmod +x build-setup.sh`
2. For MariaDB installation: ensure sudo privileges
3. Check file permissions on project directory

### Windows-Specific Issues
1. Ensure PowerShell execution policy allows script execution
2. Run Command Prompt as Administrator if needed
3. Check antivirus software isn't blocking file operations

## Configuration Files

### Key Configuration Files
- `app/src/main/resources/application.properties` - Main configuration
- `app/build.gradle.kts` - Build configuration
- `settings.gradle.kts` - Project settings

### Backup Files
The script automatically creates backups of `application.properties` before making changes:
- Format: `application.properties.backup.YYYYMMDD_HHMMSS`

## Development vs Production

### Development Setup
- Use H2 in-memory database for quick setup
- Enable H2 console for database inspection
- Use default authentication users

### Production Setup
- Use persistent MariaDB/MySQL database
- Configure proper database credentials
- Set up proper authentication (OAuth2/LDAP)
- Review security settings in `application.properties`

## Next Steps

After successful setup:

1. **Access the Application:** http://localhost:8080
2. **Login:** Use admin/admin or testuser/test123
3. **Configure Authentication:** Visit `/admin/auth-config` for OAuth2 setup
4. **Database Management:** Use appropriate database tools
5. **Development:** Start modifying the codebase as needed

For more information about authentication setup, see:
- `AUTHENTICATION_SETUP.md`
- `DYNAMIC_AUTHENTICATION_GUIDE.md`
- `ENHANCED_KEYCLOAK_CONFIG.md`