# Servicio de dominio de UPMarket (publicaciones, categorías, fotos).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# curl lo usa el healthcheck de Docker contra /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home upmarket && mkdir -p /data/fotos && chown -R upmarket /data
COPY --from=build /app/target/domain-service-upmarket-*.jar app.jar
USER upmarket
ENV FOTOS_DIR=/data/fotos SPRING_PROFILES_ACTIVE=prod
VOLUME /data/fotos
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
