# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy parent pom and install
COPY pom.xml .
COPY shared-events/pom.xml shared-events/
COPY integration-service/pom.xml integration-service/
COPY flight-ops-service/pom.xml flight-ops-service/
COPY booking-service/pom.xml booking-service/
COPY api-gateway/pom.xml api-gateway/

# Create minimal source structure for dependency resolution
RUN mkdir -p shared-events/src/main/java \
    && mkdir -p integration-service/src/main/java \
    && mkdir -p flight-ops-service/src/main/java \
    && mkdir -p booking-service/src/main/java \
    && mkdir -p api-gateway/src/main/java

# Install parent pom and download dependencies
RUN mvn -N install -DskipTests
RUN mvn dependency:go-offline -pl api-gateway -am -DskipTests || true

# Copy source code
COPY shared-events/src shared-events/src
COPY api-gateway/src api-gateway/src

# Build the application
RUN mvn clean package -pl api-gateway -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for healthchecks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built jar
COPY --from=build /app/api-gateway/target/*.jar app.jar

# Create non-root user
RUN useradd -r -u 1001 appuser
USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
