# =============================================================================
# Multi-stage Dockerfile for WiseUp Backend (Quarkus JVM mode)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 – Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy dependency manifests first to leverage Docker layer cache
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

RUN chmod +x mvnw

# Download dependencies (cached as long as pom.xml does not change)
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ---------------------------------------------------------------------------
# Stage 2 – Runtime (slim JRE image)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as a non-root user
RUN addgroup -S wiseup && adduser -S -G wiseup wiseup

WORKDIR /app

# Copy the Quarkus fast-jar layout
COPY --from=builder /build/target/quarkus-app/lib/             ./lib/
COPY --from=builder /build/target/quarkus-app/app/             ./app/
COPY --from=builder /build/target/quarkus-app/quarkus/         ./quarkus/
COPY --from=builder /build/target/quarkus-app/quarkus-run.jar  ./quarkus-run.jar

RUN chown -R wiseup:wiseup /app
USER wiseup

# ---------------------------------------------------------------------------
# Runtime configuration
# ---------------------------------------------------------------------------
EXPOSE 8080

# Health check (uses the SmallRye Health endpoint)
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/health/live || exit 1

# JVM tuning for containers:
# - UseContainerSupport: respect cgroup memory limits
# - MaxRAMPercentage: use up to 75% of container memory for heap
# - Virtual thread friendly GC (G1 is default on 21)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]
