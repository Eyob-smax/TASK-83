# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
COPY database/ database/
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache mysql-client

RUN addgroup -S eventops && adduser -S eventops -G eventops

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/database/ database/

RUN chown -R eventops:eventops /app
USER eventops

EXPOSE 8443

ENTRYPOINT ["java", "-jar", "app.jar"]
