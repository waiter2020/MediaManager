#!/bin/sh
set -e

# ── Globals ───────────────────────────────────────────────────
POSTGRES_PID=""
JAVA_PID=""
NGINX_PID=""

PGDATA="${PGDATA:-/app/data/postgres}"
POSTGRES_USER="${POSTGRES_USER:-mediamanager}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-mediamanager}"
POSTGRES_DB="${POSTGRES_DB:-mediamanager}"

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
    if [ -n "$POSTGRES_PID" ] && kill -0 "$POSTGRES_PID" 2>/dev/null; then
        kill -TERM "$POSTGRES_PID" 2>/dev/null || true
        wait "$POSTGRES_PID" 2>/dev/null || true
    fi
    echo "[entrypoint] stopped."
    exit 0
}

trap cleanup TERM INT

ensure_database() {
    su-exec postgres psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${POSTGRES_USER}') THEN
                CREATE ROLE ${POSTGRES_USER} WITH LOGIN PASSWORD '${POSTGRES_PASSWORD}';
            END IF;
        END
        \$\$;
        SELECT 'CREATE DATABASE ${POSTGRES_DB} OWNER ${POSTGRES_USER}'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${POSTGRES_DB}')\gexec
        GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB} TO ${POSTGRES_USER};
EOSQL
}

# ── Start embedded PostgreSQL ────────────────────────────────
echo "[entrypoint] starting embedded postgresql …"
mkdir -p "$PGDATA" /run/postgresql
chown -R postgres:postgres "$PGDATA" /run/postgresql

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "[entrypoint] initializing postgresql data directory …"
    su-exec postgres initdb -D "$PGDATA" --encoding=UTF8 --locale=C
    {
        echo "listen_addresses = '127.0.0.1'"
        echo "port = 5432"
    } >> "$PGDATA/postgresql.conf"
    echo "host all all 127.0.0.1/32 scram-sha-256" >> "$PGDATA/pg_hba.conf"
    echo "host all all ::1/128 scram-sha-256" >> "$PGDATA/pg_hba.conf"

    su-exec postgres postgres -D "$PGDATA" &
    POSTGRES_PID=$!

    RETRY=0
    while [ "$RETRY" -lt 30 ]; do
        if su-exec postgres pg_isready -h 127.0.0.1 -p 5432 -q; then
            break
        fi
        RETRY=$((RETRY + 1))
        sleep 1
    done

    if [ "$RETRY" -ge 30 ]; then
        echo "[entrypoint] ERROR: postgresql failed to become ready during initialization."
        exit 1
    fi

    ensure_database

    kill -TERM "$POSTGRES_PID" 2>/dev/null || true
    wait "$POSTGRES_PID" 2>/dev/null || true
    POSTGRES_PID=""
fi

su-exec postgres postgres -D "$PGDATA" &
POSTGRES_PID=$!

RETRY=0
echo "[entrypoint] waiting for postgresql (up to 30s) …"
while [ "$RETRY" -lt 30 ]; do
    if su-exec postgres pg_isready -h 127.0.0.1 -p 5432 -q; then
        echo "[entrypoint] postgresql is ready."
        break
    fi
    if ! kill -0 "$POSTGRES_PID" 2>/dev/null; then
        echo "[entrypoint] ERROR: postgresql process exited unexpectedly."
        exit 1
    fi
    RETRY=$((RETRY + 1))
    sleep 1
done

if [ "$RETRY" -ge 30 ]; then
    echo "[entrypoint] ERROR: postgresql did not become ready in 30s."
    exit 1
fi

ensure_database

# ── Start backend (background) ───────────────────────────────
echo "[entrypoint] starting backend …"
java ${JAVA_OPTS} -jar /app/app.jar &
JAVA_PID=$!

# ── Wait for backend health ─────────────────────────────────
MAX_RETRIES=60
RETRY=0
echo "[entrypoint] waiting for backend (up to ${MAX_RETRIES}s) …"
while [ "$RETRY" -lt "$MAX_RETRIES" ]; do
    if wget -q --spider http://127.0.0.1:8080/api/v1/system/status 2>/dev/null; then
        echo "[entrypoint] backend is ready."
        break
    fi
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

# ── Monitor all processes ────────────────────────────────────
while true; do
    if ! kill -0 "$POSTGRES_PID" 2>/dev/null; then
        echo "[entrypoint] ERROR: postgresql process died."
        cleanup
        exit 1
    fi
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
