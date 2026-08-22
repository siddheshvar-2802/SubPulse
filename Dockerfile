# ── Stage 1: Build Application ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Install Maven for rock-solid builds
RUN apk add --no-cache maven

# Copy pom.xml and source code
COPY pom.xml ./
COPY src/ src/

# Build executable JAR
RUN mvn clean package -DskipTests -B

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
