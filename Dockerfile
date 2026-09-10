# Stage 1: Build frontend
FROM node:26-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ARG APP_VERSION=dev
RUN echo "export const APP_VERSION = '${APP_VERSION}';" > src/environments/version.ts
RUN npm run build

# Stage 2: Build backend
FROM eclipse-temurin:25-jdk-alpine AS backend-build
WORKDIR /app
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle backend/
RUN cd backend && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/src backend/src
RUN cd backend && ./gradlew --no-daemon bootJar -x test

# Stage 3: Final image
FROM eclipse-temurin:25-jre-alpine

# su-exec: drops the Java process to PUID:PGID at runtime (see entrypoint.sh)
RUN apk add --no-cache nginx su-exec

# Copy nginx config
COPY nginx.conf /etc/nginx/http.d/default.conf

# Copy frontend build
COPY --from=frontend-build /app/dist/frontend/browser /usr/share/nginx/html/

# Copy backend JAR
COPY --from=backend-build /app/backend/build/libs/*.jar /app/app.jar

# Copy entrypoint
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Create data directory (the database now lives in its own postgres container)
RUN mkdir -p /data/uploads

# The entrypoint starts as root (nginx binds :80), creates a user matching the
# PUID/PGID env vars, then runs the Java process as that user so the invoice
# files it writes are owned by the host user rather than root.

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
