#!/bin/sh

# Runtime UID/GID for the Java process. Files written to /data/uploads are owned
# by this user, so it must match the owner of the host directory mounted there.
# Get the values on the host with: id -u && id -g
PUID="${PUID:-1000}"
PGID="${PGID:-1000}"

# Reuse the group/user if those IDs already exist in the image, create otherwise.
if ! getent group "$PGID" >/dev/null 2>&1; then
    addgroup -g "$PGID" appgroup
fi
APP_GROUP=$(getent group "$PGID" | cut -d: -f1)

if ! getent passwd "$PUID" >/dev/null 2>&1; then
    adduser -D -H -u "$PUID" -G "$APP_GROUP" appuser
fi
APP_USER=$(getent passwd "$PUID" | cut -d: -f1)

# /data/uploads is chowned NON-recursively on purpose: it is usually a bind mount
# onto a host directory that may be a whole user home. On a correctly configured
# bind mount this is a no-op; on a fresh named volume (local dev) it makes the
# directory usable. Either way, existing files are left untouched.
chown "$PUID:$PGID" /data/uploads 2>/dev/null || true

# Fail loudly rather than letting uploads break at the first invoice.
if ! su-exec "$PUID:$PGID" test -w /data/uploads; then
    echo "ERROR: /data/uploads is not writable by ${PUID}:${PGID} (${APP_USER}:${APP_GROUP})"
    echo "       Fix the host directory mounted there, e.g.:"
    echo "       chown ${PUID}:${PGID} <host-dir>"
    exit 1
fi

# Handle graceful shutdown
cleanup() {
    echo "Shutting down..."
    kill "$NGINX_PID" 2>/dev/null
    kill "$JAVA_PID" 2>/dev/null
    wait
}
trap cleanup TERM INT

# Start nginx in background (stays root: it binds privileged port 80)
nginx -g 'daemon off;' &
NGINX_PID=$!
sleep 1
if ! kill -0 $NGINX_PID 2>/dev/null; then
    echo "ERROR: nginx failed to start"
    exit 1
fi

# Start Spring Boot as the unprivileged user.
# Database connection comes from DB_URL / DB_USERNAME / DB_PASSWORD (see
# application.properties) so it stays configurable from docker-compose.
echo "Starting application as ${APP_USER}:${APP_GROUP} (${PUID}:${PGID})"
su-exec "$PUID:$PGID" java -Xmx512m -jar /app/app.jar \
  --app.upload.directory=/data/uploads &
JAVA_PID=$!

wait $JAVA_PID
