# M-SPACE 배포 이미지: React 화면 + Spring API를 이미지 하나로 (CLAUDE.md 4장, docs/배포-가이드.md)
# 빌드: docker build -t mspace:0.4.0 .
# 1단계에서 화면을 빌드하고, 2단계에서 그 결과를 Spring의 static/에 넣어 jar를 만들고, 3단계는 JRE만 담아 작게 실행해요.

# ── 1. 화면 빌드 ──────────────────────────────────────
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# 실서비스 빌드예요(VITE_DEMO 없음 → 가짜 서버 코드가 번들에 들어가지 않아요)
RUN npm run build

# ── 2. API jar 빌드 ───────────────────────────────────
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
# 의존성만 먼저 받아 두면 소스만 바뀐 재빌드가 빨라요(도커 레이어 캐시)
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist ./src/main/resources/static
# 테스트는 CI(GitHub Actions, Testcontainers)에서 돌려요. 이미지 빌드 환경에는 Docker가 없어서 여기서는 건너뛰어요
RUN ./gradlew bootJar --no-daemon -x test && cp build/libs/*.jar /app.jar

# ── 3. 실행 ───────────────────────────────────────────
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
# root가 아닌 사용자로 실행해요 (컨테이너 탈출 시 피해 줄이기)
RUN groupadd --system mspace && useradd --system --gid mspace --no-create-home mspace
WORKDIR /app
COPY --from=backend /app.jar ./app.jar
USER mspace
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
