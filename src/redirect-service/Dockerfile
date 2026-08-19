# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace/redirect-service

# 소스 변경과 분리해 의존성 레이어를 캐시한다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

# help는 설정/플러그인만 준비하고, 불필요한 테스트 의존성 해석은 피한다.
RUN --mount=type=cache,id=gradle-redirect,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew help --no-daemon --build-cache

COPY src ./src

RUN --mount=type=cache,id=gradle-redirect,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar --no-daemon --build-cache

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/redirect-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
