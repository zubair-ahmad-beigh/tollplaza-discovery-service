# Stage 1: Runtime
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="FreightFox Backend Assignment"
LABEL description="Spring Boot Toll Plaza Route Discovery Service"

WORKDIR /app

# Create non-root user for security best practice
RUN groupadd -r spring && useradd -r -g spring spring

# Copy pre-built JAR from target
COPY target/tollplaza-service-1.0.0.jar app.jar
COPY src/main/resources/data/toll_plaza_india.csv /app/data/toll_plaza_india.csv

# Change ownership to non-root user
RUN chown -R spring:spring /app

USER spring

# Expose HTTP port
EXPOSE 8080

# Configure JVM memory limits and GC with IPv4 preference for cloud/NAT environments
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true"

# Healthcheck targeting Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

