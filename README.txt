================================================================================
                    THEIA01 GOVERNANCE TOOL - USER GUIDE
================================================================================

Welcome to Theia01, a comprehensive governance tool for Information Security
compliance assessment. This tool helps you assess compliance of arbitrary 
units to self-defined security catalogs, controls, and maturity models.

================================================================================
QUICK START GUIDE
================================================================================

1. AUTOMATIC SETUP (RECOMMENDED)
   
   For Linux/macOS:
   - Open terminal in project folder
   - Run: chmod +x build-setup.sh
   - Run: ./build-setup.sh
   - Follow the on-screen instructions
   
   For Windows:
   - Open Command Prompt in project folder
   - Run: build-setup.bat
   - Follow the on-screen instructions

2. START THE APPLICATION
   
   After setup completes, start the application:
   - Linux/macOS: ./gradlew bootRun
   - Windows: gradlew.bat bootRun
   
   Or run the JAR file directly:
   java -jar app/build/libs/theia01.jar

3. ACCESS THE APPLICATION
   
   Open your web browser and go to:
   http://localhost:8080
   
   Default login credentials:
   - Username: admin
   - Password: admin
   
   Or use the test account:
   - Username: testuser  
   - Password: test123

================================================================================
WHAT IS THEIA01?
================================================================================

Theia01 is a governance tool designed for Information Security professionals 
to:

- CREATE custom security catalogs and control frameworks
- ASSESS compliance of business units, systems, or processes
- TRACK maturity levels across different security domains  
- GENERATE compliance reports and documentation
- MANAGE organizational security posture over time

Key Features:
✓ Custom security catalogs and controls
✓ Compliance assessment workflows
✓ Maturity model tracking
✓ PDF report generation
✓ User management and authentication
✓ Dynamic OAuth2/LDAP integration
✓ Responsive web interface

================================================================================
SYSTEM REQUIREMENTS
================================================================================

Minimum Requirements:
- Java 11 or higher
- 2 GB RAM
- 500 MB disk space
- Web browser (Chrome, Firefox, Safari, Edge)

Recommended:
- Java 17 or higher
- 4 GB RAM  
- 1 GB disk space
- Modern web browser

Database Options:
- MariaDB/MySQL (recommended for production)
- H2 in-memory database (development/testing)

================================================================================
DATABASE SETUP OPTIONS
================================================================================

The build script offers three database options:

1. EXISTING MARIADB/MYSQL DATABASE
   - Connect to your existing database server
   - Automatically creates database and user if needed
   - Best for production environments
   - Requires database server details

2. LOCAL MARIADB INSTALLATION (Linux/macOS only)  
   - Automatically installs MariaDB on your system
   - Sets up database and user automatically
   - Requires administrator privileges
   - Good for dedicated development machines

3. H2 IN-MEMORY DATABASE
   - No installation required
   - Perfect for testing and development
   - Data is lost when application stops
   - Access database console at: http://localhost:8080/h2-console

================================================================================
MANUAL SETUP (ADVANCED USERS)
================================================================================

If you prefer manual setup:

1. SETUP DATABASE (MariaDB/MySQL)
   
   Create database and user:
   ```sql
   CREATE DATABASE govinc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'govinc'@'localhost' IDENTIFIED BY 'your_password';
   GRANT ALL PRIVILEGES ON govinc.* TO 'govinc'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. CONFIGURE APPLICATION
   
   Edit: app/src/main/resources/application.properties
   
   Update these lines:
   spring.datasource.url=jdbc:mariadb://localhost:3306/govinc
   spring.datasource.username=govinc  
   spring.datasource.password=your_password

3. BUILD APPLICATION
   
   Linux/macOS:
   chmod +x ./gradlew
   ./gradlew clean build
   
   Windows:
   gradlew.bat clean build

4. RUN APPLICATION
   
   ./gradlew bootRun
   or
   java -jar app/build/libs/theia01.jar

================================================================================
FIRST TIME LOGIN AND SETUP
================================================================================

After starting the application:

1. INITIAL LOGIN
   - Go to: http://localhost:8080
   - Login with: admin / admin
   - Change the default password immediately

2. BASIC CONFIGURATION
   - Create your organization profile
   - Set up user accounts for your team
   - Define your security catalog structure
   - Configure assessment workflows

3. AUTHENTICATION SETUP (OPTIONAL)
   - Visit: http://localhost:8080/admin/auth-config
   - Configure OAuth2 providers (Google, Microsoft, etc.)
   - Set up LDAP integration if needed
   - Test authentication methods

4. DATABASE MANAGEMENT
   
   For H2 (development):
   - Access: http://localhost:8080/h2-console  
   - JDBC URL: jdbc:h2:mem:testdb
   - Username: sa
   - Password: (leave empty)
   
   For MariaDB/MySQL:
   - Use your database management tool
   - Connect with configured credentials

================================================================================
COMMON TASKS
================================================================================

CREATING A SECURITY CATALOG:
1. Login as admin
2. Navigate to Catalogs section
3. Click "New Catalog"  
4. Define catalog structure and controls
5. Set compliance requirements

PERFORMING AN ASSESSMENT:
1. Select target unit/system
2. Choose applicable catalog
3. Complete assessment questionnaire
4. Review and submit findings
5. Generate compliance report

MANAGING USERS:
1. Go to User Management
2. Add new users with appropriate roles
3. Configure authentication methods
4. Set access permissions

GENERATING REPORTS:
1. Navigate to Reports section
2. Select assessment or time period
3. Choose report format (PDF, Excel)
4. Download or email report

================================================================================
TROUBLESHOOTING
================================================================================

COMMON ISSUES AND SOLUTIONS:

Application won't start:
- Check Java version: java -version (needs Java 11+)
- Verify database connection
- Check port 8080 is available
- Review application.properties configuration

Database connection failed:
- Verify database server is running
- Check connection details in application.properties  
- Test database connectivity manually
- Consider using H2 fallback for development

Build errors:
- Clean build: ./gradlew clean
- Refresh dependencies: ./gradlew --refresh-dependencies  
- Check internet connection for dependency download
- Verify Gradle wrapper permissions

Login issues:
- Use default credentials: admin/admin
- Clear browser cache and cookies
- Check for case sensitivity
- Reset user password if needed

Permission denied (Linux/macOS):
- Make script executable: chmod +x build-setup.sh
- Check file ownership and permissions
- Use sudo for system-level operations if needed

Port already in use:
- Stop other applications using port 8080
- Change port in application.properties: server.port=8081
- Check for other running instances

================================================================================
GETTING HELP
================================================================================

DOCUMENTATION:
- README-BUILD.md - Detailed build instructions
- AUTHENTICATION_SETUP.md - Authentication configuration  
- DYNAMIC_AUTHENTICATION_GUIDE.md - OAuth2 setup guide
- ENHANCED_KEYCLOAK_CONFIG.md - Keycloak integration

SUPPORT:
- Check application logs for error details
- Review configuration files for issues
- Test with H2 database for isolation
- Verify system requirements are met

LOG FILES:
- Application logs appear in console output
- Check gradle build output for build issues
- Database logs in respective database system
- Web server logs in Spring Boot output

================================================================================
SECURITY CONSIDERATIONS
================================================================================

PRODUCTION DEPLOYMENT:
- Change default passwords immediately
- Use strong, unique passwords for all accounts
- Configure HTTPS/SSL encryption
- Set up proper database security
- Regularly update dependencies
- Monitor access logs
- Implement backup procedures

DEVELOPMENT ENVIRONMENT:
- H2 database is suitable for development only
- Default credentials are for testing only
- Local authentication is basic - enhance for production
- Review security settings in application.properties

================================================================================
SYSTEM ARCHITECTURE
================================================================================

TECHNOLOGY STACK:
- Backend: Spring Boot (Java)
- Database: MariaDB/MySQL or H2
- Frontend: Thymeleaf templates with HTML/CSS/JavaScript  
- Security: Spring Security with OAuth2 support
- Build: Gradle
- PDF Generation: Apache PDFBox, OpenPDF, iText
- Document Processing: Apache POI

MAIN COMPONENTS:
- Assessment Engine: Core compliance assessment logic
- Catalog Management: Security controls and frameworks
- User Management: Authentication and authorization
- Reporting Engine: PDF and Excel report generation
- Configuration: Dynamic authentication and settings

================================================================================
LICENSE AND LEGAL
================================================================================

This software is provided as-is for governance and compliance purposes.
Please ensure compliance with your organization's software usage policies.

For questions about licensing, commercial use, or support, please contact
the development team.

================================================================================
VERSION INFORMATION
================================================================================

Current Version: Development Build
Java Version: 11+ required  
Spring Boot Version: 3.2.6
Build Tool: Gradle 8.0.2

Last Updated: January 2025

================================================================================

Thank you for using Theia01 Governance Tool!
For the latest updates and documentation, check the project repository.

================================================================================