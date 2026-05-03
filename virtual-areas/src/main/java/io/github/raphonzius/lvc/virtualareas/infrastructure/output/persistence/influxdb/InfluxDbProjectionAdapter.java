package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.influxdb;

import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.InfluxDbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class InfluxDbProjectionAdapter implements InfluxProjectionPort {

    private final WriteApiBlocking writeApi;
    private final QueryApi queryApi;
    private final InfluxDbProperties props;

    private static String tagStr(com.influxdb.query.FluxRecord rec, String key) {
        Object v = rec.getValueByKey(key);
        return v != null ? v.toString() : "";
    }

    private static String fieldStr(com.influxdb.query.FluxRecord rec, String key) {
        Object v = rec.getValueByKey(key);
        return v != null ? v.toString() : "";
    }

    private static MonitoredEntityType entityType(String s) {
        try {
            return MonitoredEntityType.valueOf(s);
        } catch (Exception e) {
            return MonitoredEntityType.BUS;
        }
    }

    private static LvcOrigin lvcOrigin(String s) {
        try {
            return LvcOrigin.valueOf(s);
        } catch (Exception e) {
            return LvcOrigin.VIRTUAL;
        }
    }

    @Override
    public void writeTransition(AreaTransitionEvent event) {
        String transition = switch (event) {
            case AreaTransitionEvent.Checkin ignored -> "checkin";
            case AreaTransitionEvent.Checkout ignored -> "checkout";
        };

        Point point = Point.measurement("area_transitions")
                .addTag("area_id", event.areaId())
                .addTag("entity_type", event.entityType().name())
                .addTag("lvc", event.lvc().name())
                .addTag("transition", transition)
                .addField("entity_id", event.entityId())
                .addField("area_name", event.areaName())
                .addField("lat", event.position().lat())
                .addField("lon", event.position().lon())
                .time(event.timestamp(), WritePrecision.MS);

        if (event.position().altitudeM() != null) {
            point.addField("altitude_m", event.position().altitudeM());
        }

        writeApi.writePoint(props.bucket(), props.org(), point);
    }

    @Override
    public List<TransitionRecord> queryRecent(int limit) {
        String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -24h)
                  |> filter(fn: (r) => r._measurement == "area_transitions")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: %d)
                """, props.bucket(), limit);

        try {
            return queryApi.query(flux, props.org()).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(rec -> new TransitionRecord(
                            tagStr(rec, "area_id"),
                            fieldStr(rec, "area_name"),
                            fieldStr(rec, "entity_id"),
                            entityType(tagStr(rec, "entity_type")),
                            tagStr(rec, "transition"),
                            lvcOrigin(tagStr(rec, "lvc")),
                            rec.getTime() != null ? rec.getTime() : Instant.now()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to query InfluxDB for recent transitions: {}", e.getMessage());
            return List.of();
        }
    }
}
