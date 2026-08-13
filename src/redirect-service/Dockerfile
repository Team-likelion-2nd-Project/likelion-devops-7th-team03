# =====================================================================
# 멀티스테이지 빌드
#
# 목적: "컴파일에 필요한 무거운 도구"와 "실행에 필요한 것"을 분리해서
#       최종 이미지 크기를 줄인다 (jdk 하나로 다 하던 방식보다 훨씬 가벼워짐).
#
# 흐름: Stage 1(build)에서 jar를 만들고 → Stage 2가 그 jar만 쏙 가져와서
#       실행용으로 포장한다. Stage 1에서 쓴 컴파일러 등은 최종 이미지에
#       전혀 안 남는다.
# =====================================================================

# ===== Stage 1: 빌드 (무거움, 컴파일용) =====
# jdk 이미지를 쓴다 — jdk는 컴파일 도구(javac 등)까지 포함된 풀세트라 무겁다(약 300MB~).
# "AS build"로 이 단계에 이름표를 붙여둔다 — Stage 2에서 이 이름으로
# "여기서 만든 결과물만 가져와"라고 지목할 수 있게 하기 위함.
FROM eclipse-temurin:17-jdk AS build

# 이 단계 안에서 redirect-service를 빌드할 폴더.
# Stage 2의 /app과는 별개의, 완전히 독립된 컨테이너다.
WORKDIR /workspace/redirect-service

# Docker 빌드 컨텍스트(저장소 루트)에서 redirect-service 코드만 복사한다.
# DB 마이그레이션은 별도 배포 과정에서 적용하므로, 애플리케이션 이미지에는 포함하지 않는다.
COPY src/redirect-service/ /workspace/redirect-service/

# gradlew에 실행 권한을 준 뒤, bootJar 태스크로 실행 가능한 jar를 만든다.
# --no-daemon: CI/도커 빌드처럼 한 번 쓰고 끝나는 환경에서는 Gradle 데몬을
# 백그라운드에 남겨둘 필요가 없어서 끔(메모리 절약).
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon


# ===== Stage 2: 실행 (가벼움, 실행용) =====
# 여기서부터는 Stage 1과 완전히 별개의 새 이미지에서 시작한다.
# jre는 "이미 컴파일된 자바 프로그램을 실행만" 할 수 있는 최소 구성이라
# jdk보다 훨씬 가볍다 — 컴파일러 등 빌드 도구는 여기 아예 없다.
FROM eclipse-temurin:17-jre

# 실행 단계에서 쓸 작업 폴더 (Stage 1의 /workspace/redirect-service와 무관한 새 경로).
WORKDIR /app

# --from=build: "Stage 1(build라는 이름표 붙은 단계)의 결과물만" 가져온다.
# Stage 1에서 만든 jar 파일 하나만 쏙 복사해오고, 그 jar를 만드는 데 쓰였던
# jdk, gradle, 소스코드, 빌드 캐시 등은 전부 Stage 1과 함께 버려진다.
COPY --from=build /workspace/redirect-service/build/libs/*.jar app.jar

# 이 컨테이너가 8080 포트를 쓴다는 걸 명시(문서화 목적, 실제 포트 개방은
# docker run -p 나 K8s Service 설정에서 함).
EXPOSE 8080

# 컨테이너가 시작될 때 실행할 명령. 최종 이미지엔 이 jar 파일과
# jre(실행 환경)만 남아있는 상태이므로, 정말 필요한 것만으로 가볍게 뜬다.
ENTRYPOINT ["java", "-jar", "app.jar"]
