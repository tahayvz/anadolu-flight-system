# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom
COPY pom.xml ./

# Copy all module pom.xml files (Maven needs them for multi-module validation)
COPY shared-events/pom.xml ./shared-events/
COPY integration-service/pom.xml ./integration-service/
COPY flight-ops-service/pom.xml ./flight-ops-service/
COPY booking-service/pom.xml ./booking-service/
COPY api-gateway/pom.xml ./api-gateway/

# Copy source for modules we need to build
COPY shared-events/src ./shared-events/src
COPY flight-ops-service/src ./flight-ops-service/src

# Create empty src directories for other modules to satisfy Maven
RUN mkdir -p integration-service/src/main/java booking-service/src/main/java api-gateway/src/main/java

# Install parent pom first, then shared-events, then this service
RUN mvn -N install -DskipTests
RUN mvn -pl shared-events install -DskipTests
RUN mvn -pl flight-ops-service package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Add non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy the built jar
COPY --from=build /app/flight-ops-service/target/*.jar app.jar

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
