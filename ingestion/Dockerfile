FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY ingestion/pom.xml ingestion/
COPY scoring/pom.xml scoring/
COPY alerting/pom.xml alerting/

RUN mvn dependency:go-offline -B

COPY common/src common/src
COPY ingestion/src ingestion/src

RUN mvn -pl common,ingestion -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/ingestion/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]