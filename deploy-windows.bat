@echo off
REM KryptoLoot CEO Dashboard - Windows Production Deployment Script
REM This script starts the Flask backend with production settings on Windows

echo.
echo ========================================
echo KryptoLoot CEO Dashboard - Windows Deploy
echo ========================================
echo.

REM Check if required environment variables are set
if not defined ADMIN_USERNAME (
    echo ERROR: ADMIN_USERNAME environment variable not set
    echo Please set the following variables:
    echo   set ADMIN_USERNAME=your-username
    echo   set ADMIN_PASSWORD_HASH=your-hash
    echo   set ADMIN_SECRET_KEY=your-secret-key
    exit /b 1
)

if not defined ADMIN_PASSWORD_HASH (
    echo ERROR: ADMIN_PASSWORD_HASH environment variable not set
    exit /b 1
)

if not defined ADMIN_SECRET_KEY (
    echo ERROR: ADMIN_SECRET_KEY environment variable not set
    exit /b 1
)

REM Set production environment
set FLASK_ENV=production
set HOST=0.0.0.0
set PORT=5000

echo Starting KryptoLoot CEO Dashboard Backend
echo =========================================
echo Configuration:
echo   FLASK_ENV=%FLASK_ENV%
echo   HOST=%HOST%
echo   PORT=%PORT%
echo   ADMIN_USERNAME=%ADMIN_USERNAME%
echo   ADMIN_SECRET_KEY=***[set]***
echo.
echo Backend will be available at:
echo   http://127.0.0.1:5000/admin (local)
echo   http://[your-ip]:5000/admin (network)
echo.
echo Press Ctrl+C to stop the server
echo.

REM Start with Waitress (Windows-friendly WSGI server)
python -m waitress --host=%HOST% --port=%PORT% app:app

if errorlevel 1 (
    echo.
    echo ERROR: Failed to start Waitress server
    echo.
    echo Troubleshooting:
    echo 1. Verify Python is installed: python --version
    echo 2. Verify Waitress is installed: pip install waitress
    echo 3. Check environment variables are set
    echo.
    pause
    exit /b 1
)
