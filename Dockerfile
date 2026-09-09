# ──────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — Full JDK for compiling the application
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and POM first for dependency layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build the fat JAR (skip tests — they run in CI)
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — Minimal JRE for running the application
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
