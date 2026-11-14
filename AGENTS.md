# agents.md — virtual-ms-java

## Commands

```bash
# From the TCC root (the directory holding proto-shared/ and virtual-ms-java/).
# Build proto-shared stubs first (run once, or after any .proto change)
cd proto-shared && mvn -B -ntp install -DskipTests
cd ../virtual-ms-java/virtual-areas

# Build the app (from virtual-ms-java/virtual-areas)
mvn -B -ntp clean package -DskipTests

# Run (requires MariaDB + InfluxDB + event-hub running)
mvn spring-boot:run

# Run tests (64 tests; the outbox integration test uses Testcontainers MariaDB and is skipped without Docker)
mvn test

# Start full stack via infra-local-shared (from the TCC root)
cd infra-local-shared && docker compose up -d
```

App runs on **port 8082**. Actuator exposes only `health` (probes enabled).

- Vaadin UI: http://localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html
- gRPC server (AreaService): localhost:50052

## Architecture

Hexagonal architecture (ports & adapters), following live-ms-java conventions.

```
domain/
  area/         Area (sealed: PolygonArea | CircleArea | CorridorArea), AreaPort
  poi/          PointOfInterest, PointOfInterestPort, PoiType
  entity/       EntityState (current area memberships), EntityStatePort
  event/        AreaTransitionEvent (sealed: Checkin | Checkout), EventDispatchPort,
                SimulationEventPort, InfluxProjectionPort, TransitionRecord, LvcOrigin, MissionTarget
  geofence/     Coordinate, GeofenceCalculator (JTS polygon + haversine + corridor)
  exception/    DomainException (sealed), AreaNotFoundException, InvalidCoordinateException

application/
  service/      AreaManagementService (CRUD), GeofenceMonitoringService (core logic, writes the transactional outbox),
                EventSimulationService (Vaadin-facing simulation)
  exception/    ApplicationException (sealed hierarchy)

infrastructure/
  config/       EventHubGrpcConfiguration (ManagedChannel + stubs as beans), InfluxDbConfiguration,
                SideEffectExecutorConfiguration, properties/EventHubProperties, properties/InfluxDbProperties
  output/
    persistence/mariadb/   AreaEntity, PoiEntity (JPA + @JdbcTypeCode JSON), AreaMariadbAdapter, PoiMariadbAdapter,
                           EntityStateMariadbAdapter (JdbcTemplate upsert)
    persistence/influxdb/  InfluxDbProjectionAdapter (write + Flux query)
    grpc/                  EventHubGrpcAdapter (EventDispatchPort + SimulationEventPort),
                           EventHubSubscriberAdapter (SmartLifecycle), ProtoMapper, ExponentialBackoff
  adapter/
    web/        AreaController, PoiController, SimulationController,
                GlobalExceptionHandler, api/ (OpenAPI interfaces), dto/
    grpc/       AreaGrpcService (extends AreaServiceGrpc.AreaServiceImplBase)
  ui/           MainLayout, AreaManagementView, PoiManagementView,
                SimulationView, MonitoringView (Vaadin 25)
  exception/    InfrastructureException
```

## Key Conventions

- **Domain** has zero framework imports — only JTS (pure Java geometry lib).
- **GeofenceCalculator** handles three area types: POLYGON (JTS), CIRCLE (haversine), CORRIDOR (min-distance to polyline segment).
- **EntityState** is the write-side source of truth for "which entities are currently inside which areas." Stored in MariaDB with composite PK `(entity_id, entity_type, area_id)`. Upsert uses `ON DUPLICATE KEY UPDATE`.
- **Event-hub subscription:** `EventHubSubscriberAdapter` (SmartLifecycle) starts one virtual thread per tracked entity type (`DRONE`, `BUS`, `VESSEL`). Each thread keeps a `Subscribe` server stream open and feeds MOVE events into `GeofenceMonitoringService`. When a stream ends or fails, it re-subscribes after exponential backoff with equal jitter (`ExponentialBackoff`, `eventhub.grpc.reconnect-initial-backoff` 1s to `reconnect-max-backoff` 30s). The backoff resets only after a stream that stayed up for at least `eventhub.grpc.reconnect-reset-after` (30s), so a flapping hub cannot cause a tight reconnect loop. Dropped events are counted in `virtualareas.subscriber.dropped{reason}`. `stop()` cancels the live calls and interrupts the threads.
- **Proto mapping:** `ProtoMapper` is the anti-corruption layer between the event-hub contract and the domain. Proto enums have an implicit `UNRECOGNIZED` value: unknown values are ignored with a warning instead of defaulting to a constant (e.g. they never become `DRONE`).
- **Transactional outbox (CHECKIN/CHECKOUT):** `GeofenceMonitoringService` writes each transition to `event_outbox` (Flyway `V3__event_outbox`) in the same transaction as the `EntityState` change, so a transition is published if and only if it was committed. `OutboxRelay` delivers rows to event-hub outside any DB transaction: it claims a batch with `SELECT ... FOR UPDATE SKIP LOCKED` plus a 60s lease, sends, marks `sent_at` or reschedules with exponential backoff (`virtual-areas.outbox.backoff-initial` 1s to `backoff-max` 5min) recording `last_error`. Ordering is per entity (`entity_id` + `entity_type`): a later event never overtakes an earlier unsent one. Delivery is at-least-once; there is no dead-letter (a poison event blocks only its own entity). The relay runs every `OUTBOX_POLL_INTERVAL` (500ms) and is also nudged after each commit; sent rows older than `OUTBOX_RETENTION` (7d) are purged hourly. Metrics: `virtualareas.outbox.pending|sent|failures`.
- **InfluxDB projection:** best-effort and asynchronous after commit (virtual-thread executor, `orTimeout(DISPATCH_TIMEOUT)`), never blocks the caller or the gRPC subscriber; failures are logged with the ScopedValue entity context (JEP 506, re-bound in the task) and counted in `virtualareas.influx.failures`.
- **InfluxDB projections** bucket (shared with event-hub) receives `area_transitions` measurements. Tags: `area_id`, `entity_type`, `lvc`, `transition`. Fields: `entity_id`, `area_name`, `lat`, `lon`.
- **gRPC server** (AreaService on port 50052) is used by constructive-airsim-ms-python to check if a drone is inside any area before sending a mission. Errors map to `INVALID_ARGUMENT` (bad request) or `INTERNAL` (other failures), not `UNKNOWN`.
- **gRPC client:** Boot-managed channel `event-hub` (`spring.grpc.client.channel.event-hub`), per-call deadline `eventhub.grpc.deadline` (`EVENTHUB_DEADLINE`, default 2s).
- **Java 25:** final JEPs only; unnamed patterns and variables (JEP 456); no `--enable-preview`.

## Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
| `MARIADB_HOST` | `localhost` | MariaDB host (port 3306, database `virtual_areas`) |
| `MARIADB_USER` | `virtual_areas` | Database user |
| `MARIADB_PASSWORD` | dev default | Database password; override outside dev |
| `INFLUXDB_URL` | `http://localhost:8086` | InfluxDB URL |
| `INFLUXDB_TOKEN` | dev default | InfluxDB token; override outside dev |
| `INFLUXDB_ORG` | `tcc-org` | InfluxDB org |
| `INFLUXDB_BUCKET` | `projections` | InfluxDB bucket |
| `EVENTHUB_HOST` | `localhost` | Event-hub gRPC host |
| `EVENTHUB_PORT` | `50051` | Event-hub gRPC port |
| `EVENTHUB_DEADLINE` | `2s` | Per-call deadline for `SendEvent` |
| `DISPATCH_TIMEOUT` | `5s` | Timeout of the asynchronous InfluxDB projection of one transition |
| `OUTBOX_POLL_INTERVAL` | `500ms` | Outbox relay polling interval |
| `OUTBOX_RETENTION` | `7d` | How long sent outbox rows are kept |

## Proto files

Managed by `proto-shared/` at TCC root. Two protos:

- `event.proto` — EventHub service (client stubs used here)
- `virtual_areas.proto` — AreaService (server stubs used here)

After any proto change, from the TCC root: `cd proto-shared && mvn install -DskipTests`.

## Database

| DB | Side | User | Password |
|---|---|---|---|
| `virtual_areas` | Write | `virtual_areas` | `tcc-virtual-areas` (dev default) |

Flyway migrations in `src/main/resources/db/migration/`: `V1__schema` (idempotent DDL), `V2__seed` (seeded areas) and `V3__event_outbox` (transactional outbox). `spring.jpa.hibernate.ddl-auto` is `validate`.

## Docker and CI

- **Build context:** the TCC root (the directory holding `proto-shared/` and `virtual-ms-java/` side by side). Run from there: `docker build -f virtual-ms-java/virtual-areas/Dockerfile .`
- **Build stage:** installs `proto-shared` into the Maven cache, resolves the POM layer first, builds the jar with the Vaadin production bundle (`build-frontend` runs in `compile`; there is no production profile), and splits it into Spring Boot layers.
- **AOT cache:** a training stage trains a JEP 483/514/515 AOT cache (`/app/app.aot`) with no MariaDB, InfluxDB or event-hub: Flyway off, `ddl-auto=none`, lazy Hikari pool against a non-existent host, gRPC server on a random port, `spring.context.exit=onRefresh`. The JVM flags used for training must match the `ENTRYPOINT`. There is no AppCDS, no entrypoint script and no `/data` volume.
- **Runtime:** `liberica` JRE 25 image, runs as non-root UID 10001. `ENTRYPOINT` sets G1, compact object headers, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`, `--enable-native-access` and `-XX:AOTCache`. Extra options go through `JDK_JAVA_OPTIONS`. Exposes 8082 and 50052.
- **HEALTHCHECK:** `wget` against `http://127.0.0.1:8082/actuator/health/readiness`.
- **`.github/workflows/security.yml`:** `build` (`mvn verify` with `proto-shared` checked out next to the repo), `trivy fs` (blocking gate at HIGH/CRITICAL with a fix, plus SARIF upload), and `trivy image` (builds `virtual-ms-java/virtual-areas/Dockerfile` without push and gates it). The optional secret `PROTO_SHARED_TOKEN` is needed only when `proto-shared` is private.

## Seeded Areas

| ID                  | Name | Type | Monitors |
|---------------------|---|---|---|
| `a1b2c3d4-0001-...` | Mackenzie Campus | POLYGON (OSM 38629632) | DRONE |
| `a1b2c3d4-0002-...` | Rua da Consolação | CORRIDOR (OSM 333172735, 20m) | BUS |
| `a1b2c3d4-0003-...` | Consolação Neighborhood | POLYGON (OSM 2731076) | BUS |
| `a1b2c3d4-0004-...` | Caio Prado C/B Stop Area | CIRCLE 80m — MISSION_TRIGGER | BUS |
| `a1b2c3d4-0005-...` | Santos Port | CIRCLE 5000m | VESSEL |
