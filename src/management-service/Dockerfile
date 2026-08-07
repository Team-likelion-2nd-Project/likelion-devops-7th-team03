# 1. 실행 환경(JDK 포함 이미지)
FROM eclipse-temurin:17-jdk

# 2. 컨테이너 내부 작업 위치
WORKDIR /app

# 3. 빌드된 jar 파일 복사
COPY build/libs/*.jar app.jar

# 4. 컨테이너 실행 시 실행할 명령
ENTRYPOINT ["java", "-jar", "app.jar"]