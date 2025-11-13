@echo off
REM Setup Git hooks for version management
REM Run this script once to install git hooks

setlocal

set "HOOKS_DIR=.git\hooks"

if not exist ".git" (
    echo Error: Not in a Git repository root
    exit /b 1
)

if not exist "%HOOKS_DIR%" mkdir "%HOOKS_DIR%"

REM Note: Git hooks on Windows need special handling
REM For post-push, create a batch wrapper

echo @echo off > "%HOOKS_DIR%\post-push.bat"
echo REM Auto-increment version after push >> "%HOOKS_DIR%\post-push.bat"
echo setlocal enabledelayedexpansion >> "%HOOKS_DIR%\post-push.bat"
echo set "VERSION_FILE=version.txt" >> "%HOOKS_DIR%\post-push.bat"
echo if not exist "!VERSION_FILE!" exit /b 0 >> "%HOOKS_DIR%\post-push.bat"
echo for /f "tokens=*" %%%%a in (!VERSION_FILE!) do set "CURRENT_VERSION=%%%%a" >> "%HOOKS_DIR%\post-push.bat"
echo for /f "tokens=1,2,3 delims=." %%%%a in ("!CURRENT_VERSION!") do ( >> "%HOOKS_DIR%\post-push.bat"
echo     set "MAJOR=%%%%a" >> "%HOOKS_DIR%\post-push.bat"
echo     set "MINOR=%%%%b" >> "%HOOKS_DIR%\post-push.bat"
echo     set "PATCH=%%%%c" >> "%HOOKS_DIR%\post-push.bat"
echo ) >> "%HOOKS_DIR%\post-push.bat"
echo set /a NEW_PATCH=PATCH+1 >> "%HOOKS_DIR%\post-push.bat"
echo set "NEW_VERSION=!MAJOR!.!MINOR!.!NEW_PATCH!" >> "%HOOKS_DIR%\post-push.bat"
echo ( >> "%HOOKS_DIR%\post-push.bat"
echo     echo !NEW_VERSION! >> "%HOOKS_DIR%\post-push.bat"
echo ) ^> "!VERSION_FILE!" >> "%HOOKS_DIR%\post-push.bat"
echo echo [post-push] Version bumped from !CURRENT_VERSION! to !NEW_VERSION! >> "%HOOKS_DIR%\post-push.bat"
echo endlocal >> "%HOOKS_DIR%\post-push.bat"

echo Git hooks setup complete!
echo Version will be auto-incremented after each push.

endlocal
