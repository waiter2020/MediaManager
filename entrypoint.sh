#!/bin/sh
set -e

# ── Globals ───────────────────────────────────────────────────
JAVA_PID=""
NGINX_PID=""

# ── Cleanup on shutdown ──────────────────────────────────────
cleanup() {
    echo "[entrypoint] shutting down …"
    if [ -n "$NGINX_PID" ] && kill -0 "$NGINX_PID" 2>/dev/null; then
        kill -QUIT "$NGINX_PID" 2>/dev/null || true
    fi
    if [ -n "$JAVA_PID" ] && kill -0 "$JAVA_PID" 2>/dev/null; then
        kill -TERM "$JAVA_PID" 2>/dev/null || true
        wait "$JAVA_PID" 2>/dev/null || true
    fi
    echo "[entrypoint] stopped."
    exit 0
}

trap cleanup TERM INT

# ── Start backend (background) ───────────────────────────────
echo "[entrypoint] starting backend …"
java ${JAVA_OPTS} -jar /app/app.jar &
JAVA_PID=$!

# ── Wait for backend health ─────────────────────────────────
MAX_RETRIES=30
RETRY=0
echo "[entrypoint] waiting for backend (up to ${MAX_RETRIES}s) …"
while [ "$RETRY" -lt "$MAX_RETRIES" ]; do
    if wget -q --spider http://127.0.0.1:8080/api/v1/system/status 2>/dev/null; then
        echo "[entrypoint] backend is ready."
        break
    fi
    # check if java died while we were waiting
    if ! kill -0 "$JAVA_PID" 2>/dev/null; then
        echo "[entrypoint] ERROR: backend process exited unexpectedly."
        exit 1
    fi
    RETRY=$((RETRY + 1))
    sleep 1
done

if [ "$RETRY" -ge "$MAX_RETRIES" ]; then
    echo "[entrypoint] WARNING: backend did not become ready in ${MAX_RETRIES}s, starting nginx anyway."
fi

# ── Start nginx (background so we can monitor both) ──────────
echo "[entrypoint] starting nginx …"
nginx -g 'daemon off;' &
NGINX_PID=$!

# ── Monitor both processes ───────────────────────────────────
while true; do
    if ! kill -0 "$JAVA_PID" 2>/dev/null; then
        echo "[entrypoint] ERROR: backend process died."
        cleanup
        exit 1
    fi
    if ! kill -0 "$NGINX_PID" 2>/dev/null; then
        echo "[entrypoint] ERROR: nginx process died."
        cleanup
        exit 1
    fi
    sleep 5
done
