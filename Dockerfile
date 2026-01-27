# ========================================
# Multi-stage Dockerfile for Exchange Rates Application
# ========================================
# Stage 1: Build the application
# Stage 2: Run the application
# ========================================

# Stage 1: Build
FROM maven:3.8.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy Maven configuration files first (for better caching)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17

WORKDIR /app

# Copy the JAR from the builder stage
COPY --from=builder /app/target/testingday-exchange-rates-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Set default profile to prod (can be overridden)
ENV SPRING_PROFILES_ACTIVE=prod

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
