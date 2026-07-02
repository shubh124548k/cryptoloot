#!/bin/bash
# KryptoLoot CEO Dashboard - Linux Production Deployment Script
# This script starts the Flask backend with production settings on Linux using Gunicorn

set -e  # Exit on error

echo ""
echo "=========================================="
echo "KryptoLoot CEO Dashboard - Linux Deploy"
echo "=========================================="
echo ""

# Check if required environment variables are set
if [ -z "$ADMIN_USERNAME" ]; then
    echo "ERROR: ADMIN_USERNAME environment variable not set"
    echo "Please set the following variables:"
    echo "  export ADMIN_USERNAME=your-username"
    echo "  export ADMIN_PASSWORD_HASH=your-hash"
    echo "  export ADMIN_SECRET_KEY=your-secret-key"
    exit 1
fi

if [ -z "$ADMIN_PASSWORD_HASH" ]; then
    echo "ERROR: ADMIN_PASSWORD_HASH environment variable not set"
    exit 1
fi

if [ -z "$ADMIN_SECRET_KEY" ]; then
    echo "ERROR: ADMIN_SECRET_KEY environment variable not set"
    exit 1
fi

# Check if Gunicorn is installed
if ! command -v gunicorn &> /dev/null; then
    echo "ERROR: Gunicorn not found. Install with: pip install gunicorn"
    exit 1
fi

# Set production environment
export FLASK_ENV=production
export HOST=${HOST:-0.0.0.0}
export PORT=${PORT:-5000}
export GUNICORN_WORKERS=${GUNICORN_WORKERS:-4}

echo "Starting KryptoLoot CEO Dashboard Backend"
echo "==========================================="
echo "Configuration:"
echo "  FLASK_ENV=$FLASK_ENV"
echo "  HOST=$HOST"
echo "  PORT=$PORT"
echo "  WORKERS=$GUNICORN_WORKERS"
echo "  ADMIN_USERNAME=$ADMIN_USERNAME"
echo "  ADMIN_SECRET_KEY=***[set]***"
echo ""
echo "Backend will be available at:"
echo "  http://127.0.0.1:$PORT/admin (local)"
echo "  http://[your-ip]:$PORT/admin (network)"
echo ""
echo "View logs: tail -f app.log"
echo "Press Ctrl+C to stop the server"
echo ""

# Start Gunicorn
gunicorn \
    --workers "$GUNICORN_WORKERS" \
    --bind "$HOST:$PORT" \
    --worker-class sync \
    --timeout 120 \
    --keep-alive 5 \
    --access-logfile app.log \
    --error-logfile app.log \
    --log-level info \
    --log-file app.log \
    --capture-output \
    app:app

exit_code=$?
if [ $exit_code -ne 0 ]; then
    echo ""
    echo "ERROR: Gunicorn failed to start (exit code: $exit_code)"
    echo ""
    echo "Troubleshooting:"
    echo "1. Verify Python is installed: python3 --version"
    echo "2. Verify Gunicorn is installed: pip install gunicorn"
    echo "3. Check environment variables are set"
    echo "4. Check port $PORT is not already in use: lsof -i :$PORT"
    echo ""
    exit $exit_code
fi
