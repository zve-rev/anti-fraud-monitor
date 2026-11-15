FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY ingestion/pom.xml ingestion/
COPY scoring/pom.xml scoring/
COPY alerting/pom.xml alerting/
COPY gateway/pom.xml gateway/
COPY auth/pom.xml auth/

RUN mvn dependency:go-offline -B

COPY common/src common/src
COPY auth/src auth/src

RUN mvn -pl auth -am package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/auth/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]