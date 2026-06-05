# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY src/ src/

RUN chmod +x gradlew && ./gradlew clean bootJar -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
