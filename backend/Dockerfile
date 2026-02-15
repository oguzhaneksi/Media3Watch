# Build stage
FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /app

# Copy gradle files first for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || return 0

# Copy source code
COPY src ./src

# Build the application
RUN gradle installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S media3watch && adduser -S media3watch -G media3watch

# Copy the built distribution
COPY --from=build /app/build/install/backend/ /app/

# Set ownership
RUN chown -R media3watch:media3watch /app

USER media3watch

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["/app/bin/backend"]
