FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY auth/pom.xml auth/
COPY ingestion/pom.xml ingestion/
COPY scoring/pom.xml scoring/
COPY alerting/pom.xml alerting/
COPY gateway/pom.xml gateway/
RUN mvn -B -q dependency:go-offline -pl gateway -am

COPY common common/
COPY gateway gateway/
RUN mvn -B -q clean package -pl gateway -am -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/gateway/target/gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]