# virtual-ms-java

Digital Twin microservice for the TCC LVC platform. Geofence monitoring, area and point-of-interest management, and a Vaadin UI, with CQRS-style persistence split between MariaDB (state) and InfluxDB (time-series projections).

## Tech Stack

- **Java 25** — Final JEPs only (no preview features)
- **Spring Boot 4.1.2** — Application framework
- **Vaadin 25.3.5** — Web UI for area/POI management and monitoring
- **gRPC 1.85.1** — AreaService server + Event Hub client (protobuf 4.36.2)
- **MariaDB** — Entity state and area/POI persistence (Flyway migrations)
- **InfluxDB** — Time-series projections of area transitions
- **Hexagonal Architecture** — Ports & Adapters

## Quick Start

```bash
# Build proto-shared stubs first (from the TCC root that holds both repos)
cd proto-shared && mvn -B -ntp install -DskipTests

# Build the app
cd ../virtual-ms-java/virtual-areas && mvn -B -ntp clean package -DskipTests

# Run (requires MariaDB + InfluxDB + Event Hub)
mvn spring-boot:run
```

App runs on **port 8082**:
- Vaadin UI: http://localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html
- gRPC server (AreaService): localhost:50052

Configuration is read from environment variables (`MARIADB_*`, `INFLUXDB_*`, `EVENTHUB_*`, `DISPATCH_TIMEOUT`, `OUTBOX_*`); see `AGENTS.md` for the full list and defaults.

## Features

- **Geofence Monitoring** — Checkin/Checkout detection for drones, buses and vessels. Event-hub subscriptions reconnect with exponential backoff and jitter.
- **Area Management** — Polygon, Circle, and Corridor areas with CRUD via Vaadin UI
- **Point-of-Interest Management** — Create and manage points of interest in the UI
- **Event Dispatch** — CHECKIN/CHECKOUT events are written to a transactional outbox in the same transaction as the state change and delivered to Event Hub by a relay (per-entity ordering, exponential backoff, at-least-once). InfluxDB projections are asynchronous and best-effort.
- **gRPC AreaService** — Lets other services check whether an entity is inside an area

## Docker

- The build context is the TCC root, the directory that holds `proto-shared/` and `virtual-ms-java/`: `docker build -f virtual-ms-java/virtual-areas/Dockerfile .`
- The image trains an AOT cache at build time (no database or broker needed), so startup is faster (about 2.3 s in the measured run).
- Runs as non-root UID 10001. Exposes 8082 (HTTP/UI) and 50052 (gRPC). The HEALTHCHECK uses `/actuator/health/readiness`.

## CI

`.github/workflows/security.yml` runs `mvn verify` and two Trivy gates (filesystem and image) that block HIGH/CRITICAL vulnerabilities with an available fix.

## Documentation

Architecture details and conventions: [`AGENTS.md`](AGENTS.md). Full architecture guide: [Deepwiki](https://deepwiki.com/tcc-modern-lvc-arch/virtual-ms-java)

## License

Apache 2.0
