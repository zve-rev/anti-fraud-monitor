FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY ingestion/pom.xml ingestion/
COPY scoring/pom.xml scoring/
COPY alerting/pom.xml alerting/
COPY gateway/pom.xml gateway/

RUN mvn dependency:go-offline -B

COPY gateway/src gateway/src

RUN mvn -pl gateway -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]