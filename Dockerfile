# ── Stage 1: Build Application ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code and compile
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Lightweight Production Runtime ──────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S subpulse && adduser -S subpulse -G subpulse
USER subpulse:subpulse

# Copy compiled executable JAR from builder
COPY --from=builder /app/target/subpulse-*.jar app.jar

# Default port configuration
ENV PORT=8080
EXPOSE 8080

# Memory-efficient JVM settings for free-tier 512MB RAM instances
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
