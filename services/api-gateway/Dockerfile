# Multi-stage Docker build for API Gateway
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy only necessary files for api-gateway build
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY services/api-gateway/settings-docker.gradle settings.gradle
COPY services/api-gateway/build.gradle services/api-gateway/
COPY services/api-gateway/src services/api-gateway/src
COPY shared/common-dto/build.gradle shared/common-dto/
COPY shared/common-dto/src shared/common-dto/src
COPY shared/common-security/build.gradle shared/common-security/
COPY shared/common-security/src shared/common-security/src

# Make gradlew executable
RUN chmod +x gradlew

# Build the application
RUN ./gradlew :services:api-gateway:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Add non-root user
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/services/api-gateway/build/libs/api-gateway-*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=container"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]