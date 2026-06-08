# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# Uses Maven + JDK 17 to compile the multi-module project.
# Build context must be new parteneriat/backend/ (parent of this module).
#   docker build -f job-offer-service/Dockerfile -t ybrainy/job-offer-service:1.0.0 .
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy parent pom and all module poms so Maven can resolve the project structure.
# Source code of sibling modules is NOT needed – only their pom.xml.
COPY pom.xml                        pom.xml
COPY partnership-service/pom.xml    partnership-service/pom.xml
COPY job-offer-service/pom.xml      job-offer-service/pom.xml

# Pre-fetch dependencies into the image layer (cache-friendly).
RUN mvn -pl job-offer-service -am dependency:go-offline -q

# Now copy only this module's source code.
COPY job-offer-service/src          job-offer-service/src

# Build fat JAR, skip tests for image build.
RUN mvn -pl job-offer-service -am clean package -Dmaven.test.skip=true

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Port matches server.port default in application.yml
EXPOSE 8187

COPY --from=build /workspace/job-offer-service/target/job-offer-service-1.0.0.jar app.jar

# -Djava.security.egd speeds up startup on Linux containers
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
