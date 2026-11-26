FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml shared/
COPY auth-service/pom.xml auth-service/
COPY transaction-service/pom.xml transaction-service/
COPY risk-assessment-service/pom.xml risk-assessment-service/
COPY gateway/pom.xml gateway/

RUN mvn -B dependency:go-offline

COPY shared/src shared/src
COPY auth-service/src auth-service/src

RUN mvn -B -pl auth-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/auth-service/target/auth-service-0.0.1-SNAPSHOT-exec.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
