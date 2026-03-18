#!/bin/sh

# Handle graceful shutdown
cleanup() {
    echo "Shutting down..."
    kill "$NGINX_PID" 2>/dev/null
    kill "$JAVA_PID" 2>/dev/null
    wait
}
trap cleanup TERM INT

# Start nginx in background
nginx -g 'daemon off;' &
NGINX_PID=$!
sleep 1
if ! kill -0 $NGINX_PID 2>/dev/null; then
    echo "ERROR: nginx failed to start"
    exit 1
fi

# Start Spring Boot
java -Xmx512m -jar /app/app.jar \
  --spring.datasource.url=jdbc:h2:file:/data/db/accounting \
  --app.upload.directory=/data/uploads &
JAVA_PID=$!

wait $JAVA_PID
