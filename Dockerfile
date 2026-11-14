# Stage 1: Build JAR using Maven and JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and source code to compile inside the container
COPY pom.xml .
COPY src ./src

# Build the executable Spring Boot JAR
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="FreightFox Backend Assignment"
LABEL description="Spring Boot Toll Plaza Route Discovery Service"

WORKDIR /app

# Create non-root user for security best practice
RUN groupadd -r spring && useradd -r -g spring spring

# Copy compiled JAR from builder stage
COPY --from=builder /build/target/tollplaza-service-1.0.0.jar app.jar
COPY src/main/resources/data/toll_plaza_india.csv /app/data/toll_plaza_india.csv

# Change ownership to non-root user
RUN chown -R spring:spring /app

USER spring

# Expose HTTP port
EXPOSE 8080

# Configure JVM memory limits and GC with IPv4 preference for cloud/NAT environments
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

