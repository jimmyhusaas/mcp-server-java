# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom files first for dependency caching
COPY pom.xml .
COPY mcp-spring-boot-starter/pom.xml mcp-spring-boot-starter/
COPY mcp-server-java/pom.xml mcp-server-java/

# Download dependencies (cached as long as pom.xml doesn't change)
RUN mvn dependency:go-offline -q

# Copy source and build
COPY mcp-spring-boot-starter/src mcp-spring-boot-starter/src
COPY mcp-server-java/src mcp-server-java/src
RUN mvn package -DskipTests -q

# ---- Stage 2: Run ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the runnable JAR
COPY --from=builder /build/mcp-server-java/target/mcp-server-java-0.1.0.jar app.jar

# Render assigns PORT dynamically via environment variable
ENV SERVER_PORT=8080

EXPOSE 8080

# Run in SSE mode
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=sse"]
