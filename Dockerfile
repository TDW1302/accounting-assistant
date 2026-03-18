# Stage 1: Build frontend
FROM node:24-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
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

RUN apk add --no-cache nginx

# Copy nginx config
COPY nginx.conf /etc/nginx/http.d/default.conf

# Copy frontend build
COPY --from=frontend-build /app/dist/frontend/browser /usr/share/nginx/html/

# Copy backend JAR
COPY --from=backend-build /app/backend/build/libs/*.jar /app/app.jar

# Copy entrypoint
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Create data directories
RUN mkdir -p /data/db /data/uploads

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /data /app /usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
