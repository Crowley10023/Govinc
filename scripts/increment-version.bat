@echo off
REM Increment patch version in version.txt (x.x.x format)
REM Usage: scripts\increment-version.bat

setlocal enabledelayedexpansion

set "VERSION_FILE=version.txt"

if not exist "%VERSION_FILE%" (
    echo Error: %VERSION_FILE% not found
    exit /b 1
)

REM Read current version
for /f "tokens=*" %%a in (%VERSION_FILE%) do set "CURRENT_VERSION=%%a"

REM Parse version components
for /f "tokens=1,2,3 delims=." %%a in ("%CURRENT_VERSION%") do (
    set "MAJOR=%%a"
    set "MINOR=%%b"
    set "PATCH=%%c"
)

REM Increment patch version
set /a NEW_PATCH=PATCH+1
set "NEW_VERSION=!MAJOR!.!MINOR!.!NEW_PATCH!"

REM Update version file
(
    echo !NEW_VERSION!
) > "%VERSION_FILE%"

echo Version bumped from %CURRENT_VERSION% to !NEW_VERSION!
endlocal
