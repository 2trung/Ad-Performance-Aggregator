FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q test package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/ad-performance-aggregator-1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

