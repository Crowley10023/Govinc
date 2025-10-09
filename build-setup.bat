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

REM Function to test database connection
:test_db_connection
set "host=%1"
set "port=%2"
set "user=%3"
set "password=%4"
set "database=%5"

echo [INFO] Testing database connection...

mysql -h%host% -P%port% -u%user% -p%password% -e "USE %database%;" >nul 2>&1
if %errorlevel% equ 0 (
    echo [SUCCESS] Database connection successful!
    set DB_CONNECTION_OK=1
) else (
    echo [ERROR] Database connection failed!
    set DB_CONNECTION_OK=0
)
goto :eof

REM Function to create database and user
:create_database
set "host=%1"
set "port=%2"
set "root_password=%3"
set "db_password=%4"

echo [INFO] Creating database and user...

echo CREATE DATABASE IF NOT EXISTS %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; > temp_create_db.sql
echo CREATE USER IF NOT EXISTS '%DB_USER%'@'localhost' IDENTIFIED BY '%db_password%'; >> temp_create_db.sql
echo CREATE USER IF NOT EXISTS '%DB_USER%'@'%%' IDENTIFIED BY '%db_password%'; >> temp_create_db.sql
echo GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USER%'@'localhost'; >> temp_create_db.sql
echo GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USER%'@'%%'; >> temp_create_db.sql
echo FLUSH PRIVILEGES; >> temp_create_db.sql

mysql -h%host% -P%port% -uroot -p%root_password% < temp_create_db.sql >nul 2>&1
set create_result=%errorlevel%
del temp_create_db.sql >nul 2>&1

if %create_result% equ 0 (
    echo [SUCCESS] Database and user created successfully!
    set DB_CREATED=1
) else (
    echo [ERROR] Failed to create database and user!
    set DB_CREATED=0
)
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
    
    if !DB_CONNECTION_OK! equ 1 (
        call :update_application_properties "!db_host!" "!db_port!" "!db_user!" "!db_password!" "!db_name!"
    ) else (
        echo [WARNING] Connection failed. Would you like to create the database? (y/n)
        set /p create_db=""
        if /i "!create_db!"=="y" (
            set /p root_password="Enter MySQL root password: "
            call :create_database "!db_host!" "!db_port!" "!root_password!" "!db_password!"
            if !DB_CREATED! equ 1 (
                call :update_application_properties "!db_host!" "!db_port!" "!db_user!" "!db_password!" "!db_name!"
            ) else (
                echo [ERROR] Failed to create database. Using H2 fallback.
                call :setup_h2_fallback
            )
        ) else (
            echo [INFO] Using H2 fallback database.
            call :setup_h2_fallback
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