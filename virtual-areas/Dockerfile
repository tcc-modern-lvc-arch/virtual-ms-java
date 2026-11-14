# syntax=docker/dockerfile:1
# Build context = parent directory holding the repos side by side:
#   docker build -f virtual-ms-java/virtual-areas/Dockerfile .

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# maven:3.9-eclipse-temurin-25
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /workspace

# proto-shared protobuf stubs: compiled and installed first (changes rarely)
COPY proto-shared/ ./proto-shared/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f proto-shared/pom.xml install -DskipTests -q

# POM-only layer: dependency resolution is cached until a pom.xml changes
COPY virtual-ms-java/pom.xml               ./virtual-ms-java/pom.xml
COPY virtual-ms-java/virtual-areas/pom.xml ./virtual-ms-java/virtual-areas/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f virtual-ms-java/virtual-areas/pom.xml \
        dependency:go-offline -q 2>/dev/null || true

# Full build. The pom has no "production" profile: with Vaadin 25 the
# build-frontend goal (bound to compile) already produces the production bundle.
COPY virtual-ms-java/ ./virtual-ms-java/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    mvn -B -ntp -f virtual-ms-java/virtual-areas/pom.xml \
        package -DskipTests -q

# Split the fat JAR into layers (dependencies → loader → snapshots → application)
RUN java -Djarmode=tools -jar virtual-ms-java/virtual-areas/target/virtual-areas.jar \
        extract --layers --destination /extracted

# ─── Stage 2: AOT cache training (same JDK as runtime) ────────────────────────
# bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
FROM bellsoft/liberica-runtime-container@sha256:5321bcfb1dc6a784368a017823cb7573f42d48bcedebf75b2eebdb4af773b0c6 AS training
WORKDIR /app
COPY --from=build /extracted/dependencies/          ./
COPY --from=build /extracted/spring-boot-loader/    ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/           ./

# Training must boot the Spring context WITHOUT MariaDB / InfluxDB / event-hub:
#  - flyway off, ddl-auto none, no JDBC metadata access (the dialect is already set in application.yaml)
#  - datasource points at a non-existent host; Hikari initialization-fail-timeout=-1
#    makes pool start lazy (no connection attempt, no failure)
#  - gRPC server on a random port; the event-hub gRPC channel is lazy
#  - spring.context.exit=onRefresh stops right after context refresh
# JVM flags that affect the archive (GC, compact headers) must equal the runtime ones.
RUN java -XX:+UseG1GC -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED \
        -XX:AOTCacheOutput=/app/app.aot \
        -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.datasource.url=jdbc:mariadb://training.invalid:3306/virtual_areas \
        -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
        -Dspring.grpc.server.port=0 \
        -Dspring.cloud.vault.enabled=false \
        -Dserver.port=0 \
        -jar virtual-areas.jar

# ─── Stage 3: Runtime ─────────────────────────────────────────────────────────
# bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
FROM bellsoft/liberica-runtime-container@sha256:5321bcfb1dc6a784368a017823cb7573f42d48bcedebf75b2eebdb4af773b0c6
WORKDIR /app

# Non-root user with fixed UID; app files stay root-owned (read-only for it)
RUN addgroup -g 10001 -S app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app

# Layers ordered slowest→fastest changing
COPY --from=training /app/ ./

USER 10001:10001
EXPOSE 8082 50052

# busybox wget is available in the base image; actuator exposes only "health"
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8082/actuator/health/readiness || exit 1

# Extra options can be appended through JDK_JAVA_OPTIONS (read by the launcher).
# Changing GC / compact-headers flags invalidates the AOT cache (JVM then ignores it).
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:+UseCompactObjectHeaders", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+ExitOnOutOfMemoryError", \
    "--enable-native-access=ALL-UNNAMED", \
    "-XX:AOTCache=/app/app.aot", \
    "-jar", "virtual-areas.jar"]
