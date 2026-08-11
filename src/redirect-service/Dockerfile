FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace/redirect-service

COPY src/redirect-service/ /workspace/redirect-service/
COPY src/db/ /workspace/db/

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/redirect-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
