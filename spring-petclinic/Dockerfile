# ============================================================================
# Dockerfile - Sécurité renforcée pour Spring PetClinic
# ============================================================================

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copie des fichiers de build
COPY pom.xml mvnw ./
COPY .mvn ./.mvn

# Cache des dépendances
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

COPY src ./src

# Build avec tests (exigence TP4)
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean verify -B

# ============================================================================
# Stage 2: Runtime - Sécurité renforcée
# ============================================================================
FROM eclipse-temurin:17-jre-alpine



# Labels pour la traçabilité
LABEL org.opencontainers.image.title="Spring PetClinic" \
      org.opencontainers.image.description="Spring PetClinic Sample Application" \
      org.opencontainers.image.vendor="Spring.io" \
      org.opencontainers.image.version="4.0.0-SNAPSHOT" \
      org.opencontainers.image.source="https://github.com/spring-projects/spring-petclinic"

# Variables d'environnement
ENV SPRING_PROFILES_ACTIVE=postgres \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10 \
    SPRING_JPA_SHOW_SQL=false

# Sécurité : mise à jour des packages
RUN apk update && apk upgrade --no-cache && \
    apk add --no-cache \
        curl \
        tzdata \
        ca-certificates \
        dumb-init \
    && update-ca-certificates \
    && rm -rf /var/cache/apk/*

# Création d'un utilisateur non-root avec UID fixe
RUN addgroup -g 1000 spring && \
    adduser -D -u 1000 -G spring spring

WORKDIR /app

# Copie du JAR avec permissions sécurisées
COPY --from=builder --chown=spring:spring /build/target/spring-petclinic-*.jar app.jar

# Protection du fichier JAR
RUN chmod 644 app.jar && chown spring:spring app.jar

# Exposition du port
EXPOSE 8080

# HealthCheck avec Actuator (endpoint /actuator/health)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Utilisateur non-root
USER spring

# Dumb-init pour gérer les signaux (PID 1)
ENTRYPOINT ["/usr/bin/dumb-init", "--"]

# Command avec gestion des signaux
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]