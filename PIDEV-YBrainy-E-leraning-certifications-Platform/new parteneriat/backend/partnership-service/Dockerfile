# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# Build context must be new parteneriat/backend/ (parent of this module).
#   docker build -f partnership-service/Dockerfile -t ybrainy/partnership-service:1.0.0 .
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml                        pom.xml
COPY job-offer-service/pom.xml      job-offer-service/pom.xml
COPY partnership-service/pom.xml    partnership-service/pom.xml

RUN mvn -pl partnership-service -am dependency:go-offline -q

COPY partnership-service/src        partnership-service/src

RUN mvn -pl partnership-service -am clean package -Dmaven.test.skip=true

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

EXPOSE 8081

COPY --from=build /workspace/partnership-service/target/partnership-service-1.0.0.jar app.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
