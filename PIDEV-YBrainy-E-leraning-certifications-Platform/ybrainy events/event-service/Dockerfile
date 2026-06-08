FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY event-service/pom.xml event-service/pom.xml
COPY inscription-service/pom.xml inscription-service/pom.xml
COPY feedback-service/pom.xml feedback-service/pom.xml
RUN mvn -pl event-service -am dependency:go-offline -B
COPY event-service/src event-service/src
RUN mvn -pl event-service -am package -Dmaven.test.skip=true -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/event-service/target/*.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
