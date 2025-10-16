@echo off
setlocal enabledelayedexpansion

REM Build Setup Script for Theia01 Governance Tool (Windows)
REM This script handles database setup and application build

REM Configuration
set DB_NAME=govinc
set DB_USER=govinc
set MARIADB_PORT=3306
set APPLICATION_PROPS=app\src\main\resources\application.properties

echo ========================================
echo   Theia01 Governance Tool Build Setup  
echo ========================================
echo.

REM Check if we're in the right directory
if not exist "settings.gradle.kts" (
    echo [ERROR] This script must be run from the project root directory!
    pause
    exit /b 1
)

REM Function to check if MySQL/MariaDB is installed
:check_mariadb_installed
mysql --version >nul 2>&1
if %errorlevel% equ 0 (
    set MARIADB_INSTALLED=1
) else (
    set MARIADB_INSTALLED=0
)
goto :eof

REM Function to check if database exists
:check_database_exists
set "host=%1"
set "port=%2"
set "user=%3"
set "password=%4"
set "database=%5"

mysql -h%host% -P%port% -u%user% -p%password% -e "SHOW DATABASES LIKE '%database%';" >temp_db_check.txt 2>&1
if %errorlevel% equ 0 (
    findstr /C:"%database%" temp_db_check.txt >nul 2>&1
    if !errorlevel! equ 0 (
        set DB_EXISTS=1
    ) else (
        set DB_EXISTS=0
    )
) else (
    set DB_EXISTS=0
)
del temp_db_check.txt >nul 2>&1
goto :eof

REM Function to test database connection with detailed error reporting
:test_db_connection
set "host=%1"
set "port=%2"
set "user=%3"
set "password=%4"
set "database=%5"
set "check_db_exists=%6"
if "%check_db_exists%"=="" set check_db_exists=true

echo [INFO] Testing database connection to %host%:%port%...

REM Test basic connectivity first
mysql -h%host% -P%port% -u%user% -p%password% -e "SELECT 1;" >temp_conn_test.txt 2>&1
set conn_result=%errorlevel%

if %conn_result% neq 0 (
    echo [ERROR] Database connection failed!
    
    REM Analyze the error and provide specific feedback
    findstr /C:"Access denied" temp_conn_test.txt >nul 2>&1
    if !errorlevel! equ 0 (
        echo [ERROR]   Access denied - Invalid username or password
    ) else (
        findstr /C:"Can't connect" temp_conn_test.txt >nul 2>&1
        if !errorlevel! equ 0 (
            echo [ERROR]   Cannot connect to database server
            echo [ERROR]   Possible causes:
            echo [ERROR]   - Database server is not running
            echo [ERROR]   - Wrong host or port
            echo [ERROR]   - Firewall blocking connection
        ) else (
            findstr /C:"Unknown database" temp_conn_test.txt >nul 2>&1
            if !errorlevel! equ 0 (
                echo [ERROR]   Database '%database%' does not exist
            ) else (
                echo [ERROR]   Connection error - check temp_conn_test.txt for details
            )
        )
    )
    set DB_CONNECTION_OK=0
    set DB_CONNECTION_CODE=1
) else (
    REM Connection successful, now check database
    if "%check_db_exists%"=="true" (
        call :check_database_exists "%host%" "%port%" "%user%" "%password%" "%database%"
        if !DB_EXISTS! equ 1 (
            echo [SUCCESS] Database connection successful! Database '%database%' exists.
            set DB_CONNECTION_OK=1
            set DB_CONNECTION_CODE=0
        ) else (
            echo [WARNING] Database connection successful, but database '%database%' does not exist.
            set DB_CONNECTION_OK=0
            set DB_CONNECTION_CODE=2
        )
    ) else (
        mysql -h%host% -P%port% -u%user% -p%password% -e "USE %database%;" >nul 2>&1
        if !errorlevel! equ 0 (
            echo [SUCCESS] Database connection successful! Database '%database%' is accessible.
            set DB_CONNECTION_OK=1
            set DB_CONNECTION_CODE=0
        ) else (
            echo [WARNING] Database connection successful, but cannot access database '%database%'.
            echo [WARNING]   This might be due to missing permissions or the database not existing.
            set DB_CONNECTION_OK=0
            set DB_CONNECTION_CODE=2
        )
    )
)

del temp_conn_test.txt >nul 2>&1
goto :eof

REM Function to create database and user with detailed error handling
:create_database
set "host=%1"
set "port=%2"
set "root_password=%3"
set "db_password=%4"

echo [INFO] Creating database and user...

REM Test root connection first
echo [INFO] Testing root connection...
mysql -h%host% -P%port% -uroot -p%root_password% -e "SELECT 1;" >temp_root_test.txt 2>&1

if %errorlevel% neq 0 (
    echo [ERROR] Cannot connect as root user!
    findstr /C:"Access denied" temp_root_test.txt >nul 2>&1
    if !errorlevel! equ 0 (
        echo [ERROR]   Root password is incorrect
    ) else (
        echo [ERROR]   Check temp_root_test.txt for error details
    )
    set DB_CREATED=0
    del temp_root_test.txt >nul 2>&1
    goto :eof
)
del temp_root_test.txt >nul 2>&1

REM Check if database already exists
call :check_database_exists "%host%" "%port%" "root" "%root_password%" "%DB_NAME%"
if !DB_EXISTS! equ 1 (
    echo [WARNING] Database '%DB_NAME%' already exists.
    echo [INFO] Ensuring user '%DB_USER%' has proper permissions...
)

REM Create database and user
echo CREATE DATABASE IF NOT EXISTS %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; > temp_create_db.sql
echo CREATE USER IF NOT EXISTS '%DB_USER%'@'localhost' IDENTIFIED BY '%db_password%'; >> temp_create_db.sql
echo CREATE USER IF NOT EXISTS '%DB_USER%'@'%%' IDENTIFIED BY '%db_password%'; >> temp_create_db.sql
echo GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USER%'@'localhost'; >> temp_create_db.sql
echo GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USER%'@'%%'; >> temp_create_db.sql
echo FLUSH PRIVILEGES; >> temp_create_db.sql

mysql -h%host% -P%port% -uroot -p%root_password% < temp_create_db.sql >temp_create_output.txt 2>&1
set create_result=%errorlevel%
del temp_create_db.sql >nul 2>&1

if %create_result% equ 0 (
    echo [SUCCESS] Database and user created/updated successfully!
    
    REM Verify the setup by testing the new user connection
    echo [INFO] Verifying new user connection...
    call :test_db_connection "%host%" "%port%" "%DB_USER%" "%db_password%" "%DB_NAME%" "false"
    if !DB_CONNECTION_OK! equ 1 (
        echo [SUCCESS] User verification successful!
        set DB_CREATED=1
    ) else (
        echo [WARNING] Database created but user verification failed. This might be normal for new installations.
        set DB_CREATED=1
    )
) else (
    echo [ERROR] Failed to create database and user!
    echo [ERROR] Check temp_create_output.txt for error details
    set DB_CREATED=0
)

del temp_create_output.txt >nul 2>&1
goto :eof

REM Function to update application.properties
:update_application_properties
set "host=%1"
set "port=%2"
set "user=%3"
set "password=%4"
set "database=%5"

echo [INFO] Updating application.properties...

REM Create backup
copy "%APPLICATION_PROPS%" "%APPLICATION_PROPS%.backup.%date:~-4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%" >nul 2>&1

REM Create temporary PowerShell script to update properties
echo $content = Get-Content '%APPLICATION_PROPS%' > update_props.ps1
echo $content = $content -replace '^spring\.datasource\.url=.*', 'spring.datasource.url=jdbc:mariadb://%host%:%port%/%database%' >> update_props.ps1
echo $content = $content -replace '^spring\.datasource\.username=.*', 'spring.datasource.username=%user%' >> update_props.ps1
echo $content = $content -replace '^spring\.datasource\.password=.*', 'spring.datasource.password=%password%' >> update_props.ps1
echo $content ^| Set-Content '%APPLICATION_PROPS%' >> update_props.ps1

powershell -ExecutionPolicy Bypass -File update_props.ps1 >nul 2>&1
del update_props.ps1 >nul 2>&1

echo [SUCCESS] Application properties updated!
goto :eof

REM Function to setup H2 database (fallback)
:setup_h2_fallback
echo [INFO] Setting up H2 in-memory database as fallback...

REM Create backup
copy "%APPLICATION_PROPS%" "%APPLICATION_PROPS%.backup.%date:~-4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%" >nul 2>&1

REM Create temporary PowerShell script to update to H2 configuration
echo $content = Get-Content '%APPLICATION_PROPS%' > update_h2.ps1
echo $content = $content -replace '^spring\.datasource\.url=.*', 'spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' >> update_h2.ps1
echo $content = $content -replace '^spring\.datasource\.username=.*', 'spring.datasource.username=sa' >> update_h2.ps1
echo $content = $content -replace '^spring\.datasource\.password=.*', 'spring.datasource.password=' >> update_h2.ps1
echo $content = $content -replace '^spring\.datasource\.driver-class-name=.*', 'spring.datasource.driver-class-name=org.h2.Driver' >> update_h2.ps1
echo $content ^| Set-Content '%APPLICATION_PROPS%' >> update_h2.ps1

powershell -ExecutionPolicy Bypass -File update_h2.ps1 >nul 2>&1
del update_h2.ps1 >nul 2>&1

REM Add H2 console configuration
echo. >> "%APPLICATION_PROPS%"
echo # H2 Database Configuration >> "%APPLICATION_PROPS%"
echo spring.h2.console.enabled=true >> "%APPLICATION_PROPS%"
echo spring.h2.console.path=/h2-console >> "%APPLICATION_PROPS%"
echo spring.h2.console.settings.web-allow-others=false >> "%APPLICATION_PROPS%"

REM Check and add H2 dependency to build.gradle.kts if not present
findstr /C:"com.h2database:h2" app\build.gradle.kts >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Adding H2 dependency to build.gradle.kts...
    
    REM Create PowerShell script to add H2 dependency
    echo $content = Get-Content 'app\build.gradle.kts' > add_h2_dep.ps1
    echo $newContent = @() >> add_h2_dep.ps1
    echo foreach ($line in $content) { >> add_h2_dep.ps1
    echo     $newContent += $line >> add_h2_dep.ps1
    echo     if ($line -match 'implementation\("org\.mariadb\.jdbc:mariadb-java-client') { >> add_h2_dep.ps1
    echo         $newContent += '    runtimeOnly("com.h2database:h2") // H2 Database for development/testing' >> add_h2_dep.ps1
    echo     } >> add_h2_dep.ps1
    echo } >> add_h2_dep.ps1
    echo $newContent ^| Set-Content 'app\build.gradle.kts' >> add_h2_dep.ps1
    
    powershell -ExecutionPolicy Bypass -File add_h2_dep.ps1 >nul 2>&1
    del add_h2_dep.ps1 >nul 2>&1
)

echo [SUCCESS] H2 database configured as fallback!
echo [INFO] Access H2 console at: http://localhost:8080/h2-console
echo [INFO] JDBC URL: jdbc:h2:mem:testdb
echo [INFO] Username: sa
echo [INFO] Password: (empty)
goto :eof

REM Main database setup function
:setup_database
echo Database Setup Options:
echo 1. Connect to existing MariaDB/MySQL database
echo 2. Use H2 in-memory database (development only)
echo 3. Manual database configuration
echo.
set /p choice="Please choose an option (1-3): "

if "%choice%"=="1" (
    echo [INFO] Connecting to existing database...
    
    set max_retries=3
    set retry_count=0
    set connection_successful=0
    
    :retry_connection
    if !retry_count! gtr 0 (
        echo.
        echo [INFO] Retry attempt !retry_count! of !max_retries!...
    )
    
    set /p db_host="Database host (default: localhost): "
    if "!db_host!"=="" set db_host=localhost
    
    set /p db_port="Database port (default: 3306): "
    if "!db_port!"=="" set db_port=3306
    
    set /p db_name="Database name (default: %DB_NAME%): "
    if "!db_name!"=="" set db_name=%DB_NAME%
    
    set /p db_user="Database user (default: %DB_USER%): "
    if "!db_user!"=="" set db_user=%DB_USER%
    
    set /p db_password="Database password: "
    
    call :test_db_connection "!db_host!" "!db_port!" "!db_user!" "!db_password!" "!db_name!"
    
    if !DB_CONNECTION_CODE! equ 0 (
        REM Success - database exists and is accessible
        call :update_application_properties "!db_host!" "!db_port!" "!db_user!" "!db_password!" "!db_name!"
        set connection_successful=1
    ) else if !DB_CONNECTION_CODE! equ 2 (
        REM Connection OK but database missing or inaccessible
        echo.
        echo [WARNING] The database connection works, but database '!db_name!' is not accessible.
        echo [INFO] Options:
        echo 1. Create the database (requires root access)
        echo 2. Try different database credentials
        echo 3. Use H2 fallback database
        set /p db_option="Choose option (1-3): "
        
        if "!db_option!"=="1" (
            set /p root_password="Enter MySQL root password: "
            call :create_database "!db_host!" "!db_port!" "!root_password!" "!db_password!"
            if !DB_CREATED! equ 1 (
                call :update_application_properties "!db_host!" "!db_port!" "!db_user!" "!db_password!" "!db_name!"
                set connection_successful=1
            ) else (
                echo [ERROR] Database creation failed.
                set /a retry_count=retry_count+1
            )
        ) else if "!db_option!"=="2" (
            set /a retry_count=retry_count+1
        ) else if "!db_option!"=="3" (
            echo [INFO] Using H2 fallback database.
            call :setup_h2_fallback
            set connection_successful=1
        ) else (
            set /a retry_count=retry_count+1
        )
    ) else (
        REM Connection failed entirely
        set /a retry_count=retry_count+1
        if !retry_count! lss !max_retries! (
            echo.
            set /p retry_choice="Would you like to retry with different settings? (y/n): "
            if /i not "!retry_choice!"=="y" (
                goto :connection_failed
            )
        )
    )
    
    if !connection_successful! equ 0 (
        if !retry_count! lss !max_retries! (
            goto :retry_connection
        )
    )
    
    :connection_failed
    if !connection_successful! equ 0 (
        echo.
        echo [ERROR] Maximum retry attempts reached or user chose not to retry.
        echo [INFO] Options:
        echo 1. Use H2 fallback database (recommended for development)
        echo 2. Exit and configure database manually
        set /p fallback_option="Choose option (1-2): "
        
        if "!fallback_option!"=="1" (
            echo [INFO] Using H2 fallback database.
            call :setup_h2_fallback
        ) else (
            echo [ERROR] Please configure the database manually and run the script again.
            pause
            exit /b 1
        )
    )
) else if "%choice%"=="2" (
    call :setup_h2_fallback
) else if "%choice%"=="3" (
    echo [INFO] Please manually configure the database settings in:
    echo %APPLICATION_PROPS%
    echo.
    echo [INFO] Make sure the database exists and the user has proper permissions.
    pause
) else (
    echo [ERROR] Invalid choice. Using H2 fallback.
    call :setup_h2_fallback
)

goto :eof

REM Function to build the application
:build_application
echo [INFO] Building the application...

echo [INFO] Running Gradle build...
call gradlew.bat clean build -x test

if %errorlevel% equ 0 (
    echo [SUCCESS] Application built successfully!
    set BUILD_SUCCESS=1
) else (
    echo [ERROR] Build failed!
    set BUILD_SUCCESS=0
)
goto :eof

REM Function to run tests
:run_tests
echo [INFO] Running tests...
call gradlew.bat test

if %errorlevel% equ 0 (
    echo [SUCCESS] All tests passed!
) else (
    echo [WARNING] Some tests failed, but continuing...
)
goto :eof

REM Main execution
:main
REM Check if MariaDB is installed
call :check_mariadb_installed

REM Setup database
call :setup_database

echo.
echo [INFO] Building application...

REM Build application
call :build_application

if !BUILD_SUCCESS! equ 1 (
    echo.
    set /p run_test="Would you like to run tests? (y/n): "
    if /i "!run_test!"=="y" (
        call :run_tests
    )
    
    echo.
    echo ========================================
    echo   Build Setup Completed Successfully!  
    echo ========================================
    echo.
    echo To start the application:
    echo   gradlew.bat bootRun
    echo   or
    echo   java -jar app\build\libs\theia01.jar
    echo.
    echo Application will be available at: http://localhost:8080
    echo.
    
    REM Show database info
    findstr /C:"h2:mem" "%APPLICATION_PROPS%" >nul 2>&1
    if %errorlevel% equ 0 (
        echo [INFO] Database: H2 in-memory (development mode)
        echo [INFO] H2 Console: http://localhost:8080/h2-console
    ) else (
        for /f "tokens=2 delims==" %%a in ('findstr "^spring.datasource.url=" "%APPLICATION_PROPS%"') do (
            echo [INFO] Database: %%a
        )
    )
) else (
    echo [ERROR] Build failed! Please check the errors above.
    pause
    exit /b 1
)

echo.
pause
goto :eof

REM Call main function
call :main