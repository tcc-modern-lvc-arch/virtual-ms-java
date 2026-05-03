# agents.md — virtual-areas-ms-java

## Commands

```bash
# Build proto-shared stubs first (run once, or after any .proto change)
cd ../proto-shared && mvn install -DskipTests

# Build the app
cd app && mvn clean package -DskipTests

# Run (requires MariaDB + InfluxDB + event-hub running)
cd app && mvn spring-boot:run

# Run tests
cd app && mvn test

# Start full stack via infra-local-shared
cd ../../infra-local-shared && docker compose up -d
```

App runs on **port 8082**.

- Vaadin UI: http://localhost:8082
- Swagger UI: http://localhost:8082/swagger-ui.html
- gRPC server (AreaService): localhost:50052

## Architecture

Hexagonal architecture (ports & adapters), following live-ms-java conventions.

```
domain/
  area/         Area (sealed: PolygonArea | CircleArea | CorridorArea), AreaPort
  poi/          PointOfInterest, PointOfInterestPort
  entity/       EntityState (current area memberships), EntityStatePort
  event/        AreaTransitionEvent (sealed: Checkin | Checkout), EventDispatchPort,
                SimulationEventPort, InfluxProjectionPort, TransitionRecord, LvcOrigin
  geofence/     Coordinate, GeofenceCalculator (JTS polygon + haversine + corridor)
  exception/    DomainException (sealed), AreaNotFoundException, InvalidCoordinateException

application/
  service/      AreaManagementService (CRUD), GeofenceMonitoringService (core logic),
                EventSimulationService (Vaadin-facing simulation)
  exception/    ApplicationException (sealed hierarchy)

infrastructure/
  config/       EventHubGrpcConfiguration (ManagedChannel + stubs as beans),
                InfluxDbConfiguration, properties/EventHubProperties, InfluxDbProperties
  output/
    persistence/mariadb/   AreaEntity, PoiEntity (JPA + @JdbcTypeCode JSON),
                           EntityStateMariadbAdapter (JdbcTemplate upsert)
    persistence/influxdb/  InfluxDbProjectionAdapter (write + Flux query)
    grpc/                  EventHubGrpcAdapter (EventDispatchPort + SimulationEventPort),
                           EventHubSubscriberAdapter (InitializingBean, virtual threads)
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
- **GeofenceCalculator** handles three area types: POLYGON (JTS), CIRCLE (haversine), CORRIDOR (min-distance to polyline
  segment).
- **EntityState** is the write-side source of truth for "which entities are currently inside which areas." Stored in
  MariaDB with composite PK `(entity_id, entity_type, area_id)`. Upsert uses `ON DUPLICATE KEY UPDATE`.
- **EventHubSubscriberAdapter** starts one virtual thread per monitored entity type at startup (`InitializingBean`).
  Each thread maintains a persistent gRPC subscription stream and reconnects on error.
- **InfluxDB projections** bucket (shared with event-hub) receives `area_transitions` measurements. Tags: `area_id`,
  `entity_type`, `lvc`, `transition`. Fields: `entity_id`, `area_name`, `lat`, `lon`.
- **gRPC server** (AreaService on port 50052) is used by constructive-airsim-ms-python to check if a drone is inside any
  area before sending a mission.

## Proto files

Managed by `proto-shared/` at TCC root. Two protos:

- `event.proto` — EventHub service (client stubs used here)
- `virtual_areas.proto` — AreaService (server stubs used here)

After any proto change: `cd proto-shared && mvn install -DskipTests`.

## Database

| DB | Side | User | Password |
|---|---|---|---|
| `virtual_areas` | Write | `virtual_areas` | `tcc-virtual-areas` |

Flyway migrations in `src/main/resources/db/migration/`.

## Seeded Areas

| ID                  | Name | Type | Monitors |
|---------------------|---|---|---|
| `a1b2c3d4-0001-...` | Mackenzie Campus | POLYGON (OSM 38629632) | DRONE |
| `a1b2c3d4-0002-...` | Rua da Consolação | CORRIDOR (OSM 333172735, 20m) | BUS |
| `a1b2c3d4-0003-...` | Consolação Neighborhood | POLYGON (OSM 2731076) | BUS |
| `a1b2c3d4-0004-...` | Caio Prado C/B Stop Area | CIRCLE 80m — MISSION_TRIGGER | BUS |
| `a1b2c3d4-0005-...` | Santos Port | CIRCLE 5000m | VESSEL |
