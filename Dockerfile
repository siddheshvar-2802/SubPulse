# ── Stage 1: Build Application ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy POM and pre-fetch dependencies for fast layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build production JAR
COPY src/ src/
RUN mvn package -DskipTests -B

# ── Stage 2: Lightweight Production Runtime ──────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as secure non-root user
RUN addgroup -S subpulse && adduser -S subpulse -G subpulse
USER subpulse:subpulse

# Copy compiled executable JAR from builder
COPY --from=builder /app/target/subpulse-*.jar app.jar

# Port configuration
ENV PORT=8080
EXPOSE 8080

# Production memory-efficient JVM settings
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

