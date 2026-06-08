# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src src
RUN mvn clean package -Dmaven.test.skip=true

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
EXPOSE 8095
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]