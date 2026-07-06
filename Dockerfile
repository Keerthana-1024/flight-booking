# ─── Stage 1: Build React frontend ────────────────────────────────────
FROM node:20-alpine AS ui-builder

WORKDIR /app/ui
COPY ui/package*.json ./
RUN npm ci --silent

COPY ui/ ./
# Build React directly into Spring Boot's static resource folder
RUN npm run build -- --outDir /app/static --emptyOutDir

# ─── Stage 2: Build Spring Boot JAR ───────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS backend-builder

WORKDIR /app

# Copy pom.xml and download dependencies first (layer-cache friendly)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source
COPY src/ src/

# Copy React build output into static resources
COPY --from=ui-builder /app/static src/main/resources/static/

# Build JAR (skip tests — they need a live DB)
RUN mvn clean package -DskipTests -q

# ─── Stage 3: Runtime image ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the fat JAR from build stage
COPY --from=backend-builder /app/target/flights-1.0.0.jar app.jar

# Render sets $PORT dynamically; Spring reads it from application.properties
EXPOSE 8080

# -Xmx256m keeps heap within Render free tier's 512 MB RAM limit
ENTRYPOINT ["java", "-Xmx256m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
