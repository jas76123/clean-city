# Multi-stage build для backend (Ktor + Exposed + Postgres + S3).
#
# 1) cache-deps: тянет зависимости в отдельном слое — gradle-files,
#    при их неизменности `docker build` переиспользует слой.
# 2) build: компилирует sources и собирает distribution.
# 3) runtime: JRE alpine + non-root user + HEALTHCHECK.

# ---------- 1. dependencies cache ----------
FROM eclipse-temurin:17-jdk-jammy AS deps

WORKDIR /workspace

COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY backend/build.gradle.kts backend/
COPY shared/build.gradle.kts shared/

# Прогрев Gradle-кэша; `|| true` чтобы первый запуск не падал из-за частичных
# модулей без sources. Реальная компиляция произойдёт в `build`-стейдже.
RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon :backend:dependencies :shared:jvmJar -x test || true

# ---------- 2. build ----------
FROM deps AS build

COPY shared/src shared/src
COPY backend/src backend/src

RUN ./gradlew --no-daemon :backend:installDist -x test

# ---------- 3. runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

# Non-root user
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/backend/build/install/backend ./

# /app/uploads — для LocalStorage режима в DEV. В PROD используется S3.
RUN mkdir -p /app/uploads && chown app:app /app/uploads

USER app

EXPOSE 8080

# Healthcheck лезет в /health (без auth, без security headers'ом проверки).
# Порт берётся из KTOR_PORT (прокидывается через env_file в docker-compose);
# fallback 8080 — дефолт Ktor, если переменная не задана.
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS http://localhost:${KTOR_PORT:-8080}/health || exit 1

# Ktor читает порт из application.conf / ENV; default 8080.
ENTRYPOINT ["./bin/backend"]
