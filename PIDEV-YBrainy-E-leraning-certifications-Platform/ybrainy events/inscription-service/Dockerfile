FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY event-service/pom.xml event-service/pom.xml
COPY inscription-service/pom.xml inscription-service/pom.xml
COPY feedback-service/pom.xml feedback-service/pom.xml
RUN mvn -pl inscription-service -am dependency:go-offline -B
COPY inscription-service/src inscription-service/src
RUN mvn -pl inscription-service -am package -Dmaven.test.skip=true -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/inscription-service/target/*.jar app.jar
EXPOSE 9002
ENTRYPOINT ["java", "-jar", "app.jar"]
