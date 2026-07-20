FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :backend:installBackend :app:webApp:wasmJsBrowserDistribution

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend/build/install/backend/lib/ ./backend/lib/
COPY --from=build /workspace/app/webApp/build/dist/wasmJs/productionExecutable/ ./web/
ENV PORT=8080
ENV WEB_ASSETS_DIR=/app/web
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/backend/lib/*", "dev.fopwoc.chronosplit.backend.ApplicationKt"]
