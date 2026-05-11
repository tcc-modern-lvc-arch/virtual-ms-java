# virtual-ms-java

Digital Twin microservice for the TCC LVC platform. Implements CQRS Write/Read segregation with geofence monitoring, area management, and point-of-interest tracking.

## Tech Stack

- **Java 25** — Latest LTS
- **Spring Boot 4** — Application framework
- **Vaadin 25.1** — Web UI for area/POI management
- **gRPC** — Area service + Event Hub integration
- **MariaDB** — CQRS Write (eventhub) + Read (eventhub_read)
- **InfluxDB** — Time-series projections
- **Hexagonal Architecture** — Ports & Adapters

## Quick Start

```bash
# Build proto-shared stubs first
cd ../proto-shared && mvn install -DskipTests

# Build the app
cd virtual-areas && mvn clean package -DskipTests

# Run (requires MariaDB + InfluxDB + Event Hub)
cd virtual-areas && mvn spring-boot:run
```

App runs on **port 8082**:
- Vaadin UI: http://localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html
- gRPC server (AreaService): localhost:50052

## Features

- **Geofence Monitoring** — Checkin/Checkout detection for vessels, drones, and buses
- **Area Management** — Polygon, Circle, and Corridor areas with CRUD via Vaadin UI
- **CQRS Projections** — Materialized views in `eventhub_read` for Grafana dashboards
- **Scoped Values** (JEP 506) — Context propagation across virtual threads

## Documentation

Full architecture guide: [Deepwiki](https://deepwiki.com/tcc-modern-lvc-arch/virtual-ms-java)

## License

Apache 2.0
