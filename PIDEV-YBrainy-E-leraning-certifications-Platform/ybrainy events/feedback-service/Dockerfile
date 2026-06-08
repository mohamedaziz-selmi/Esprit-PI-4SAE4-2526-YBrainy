FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY event-service/pom.xml event-service/pom.xml
COPY inscription-service/pom.xml inscription-service/pom.xml
COPY feedback-service/pom.xml feedback-service/pom.xml
RUN mvn -pl feedback-service -am dependency:go-offline -B
COPY feedback-service/src feedback-service/src
RUN mvn -pl feedback-service -am package -Dmaven.test.skip=true -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/feedback-service/target/*.jar app.jar
EXPOSE 9004
ENTRYPOINT ["java", "-jar", "app.jar"]
