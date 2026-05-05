# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# proto-shared protobuf stubs: compiled and installed first (changes rarely)
COPY proto-shared/ ./proto-shared/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f proto-shared/pom.xml install -DskipTests -q

# POM-only layer: Maven dependency resolution is cached until any pom.xml changes
COPY virtual-ms-java/pom.xml               ./virtual-ms-java/pom.xml
COPY virtual-ms-java/virtual-areas/pom.xml ./virtual-ms-java/virtual-areas/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f virtual-ms-java/virtual-areas/pom.xml \
        dependency:go-offline -q 2>/dev/null || true

# Full build — Vaadin production bundle (inlined JS/CSS, no dev-mode server)
COPY virtual-ms-java/ ./virtual-ms-java/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    mvn -f virtual-ms-java/virtual-areas/pom.xml \
        package -DskipTests -Pproduction -q

# ─── Stage 2: Spring Boot layer extraction ────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl AS layers
WORKDIR /app
COPY --from=build /workspace/virtual-ms-java/virtual-areas/target/virtual-areas.jar app.jar
# Splits the fat JAR into four change-frequency buckets for Docker layer reuse
RUN java -Djarmode=layertools -jar app.jar extract

# ─── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
WORKDIR /app

# Layers ordered slowest→fastest changing: only 'application' is rebuilt on
# code changes; library and loader layers are reused from the Docker cache.
COPY --from=layers /app/dependencies/          ./
COPY --from=layers /app/spring-boot-loader/    ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/           ./

EXPOSE 8082 50052

# AppCDS archive lives on a named volume so it survives container restarts.
# entrypoint.sh generates it on first boot (MariaDB already up), then reuses it.
# Mount: -v virtual-areas-cds:/data  (see docker-compose.yml)
VOLUME ["/data"]

COPY virtual-ms-java/virtual-areas/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# G1GC flags and CDS logic are in entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
