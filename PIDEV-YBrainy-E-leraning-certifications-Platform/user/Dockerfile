FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -B

# Debian (jammy) instead of Alpine: needs Python 3 + OpenCV for face biometric
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-opencv \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
COPY scripts ./scripts
COPY src/main/resources/biometrics ./biometrics
EXPOSE 8899
ENTRYPOINT ["java", "-jar", "app.jar"]
