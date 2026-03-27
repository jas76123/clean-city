FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :backend:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/build/install/backend/ ./
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["./bin/backend"]
