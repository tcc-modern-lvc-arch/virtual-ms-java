package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.influxdb;

import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import io.github.raphonzius.lvc.virtualareas.LogCapture;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.InfluxDbProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfluxDbProjectionAdapterTest {

    private final QueryApi queryApi = mock(QueryApi.class);
    private final InfluxDbProjectionAdapter adapter = new InfluxDbProjectionAdapter(
            mock(WriteApiBlocking.class), queryApi, props());

    private static InfluxDbProperties props() {
        var props = mock(InfluxDbProperties.class);
        when(props.bucket()).thenReturn("b");
        when(props.org()).thenReturn("org");
        return props;
    }

    private static FluxTable table(String entityType, String lvc) {
        var rec = new FluxRecord(0);
        rec.getValues().put("area_id", "a1");
        rec.getValues().put("area_name", "Stop");
        rec.getValues().put("entity_id", "e1");
        rec.getValues().put("entity_type", entityType);
        rec.getValues().put("transition", "checkin");
        rec.getValues().put("lvc", lvc);
        rec.getValues().put("_time", Instant.parse("2026-11-14T12:00:00Z"));
        var table = new FluxTable();
        table.getRecords().add(rec);
        return table;
    }

    @Test
    void unknownEntityTypeAndLvcAreReclassifiedWithAWarning() {
        when(queryApi.query(anyString(), eq("org"))).thenReturn(List.of(table("SPACESHIP", "ALIEN")));
        var logs = LogCapture.attach(InfluxDbProjectionAdapter.class);

        var records = adapter.queryRecent(10);

        assertThat(records).singleElement().satisfies(r -> {
            assertThat(r.entityType()).isEqualTo(MonitoredEntityType.BUS);
            assertThat(r.lvc()).isEqualTo(LvcOrigin.VIRTUAL);
        });
        assertThat(logs.warnings()).extracting(e -> e.getFormattedMessage())
                .anySatisfy(m -> assertThat(m).contains("SPACESHIP").contains("BUS"))
                .anySatisfy(m -> assertThat(m).contains("ALIEN").contains("VIRTUAL"));
        logs.detach();
    }

    @Test
    void knownValuesDoNotWarn() {
        when(queryApi.query(anyString(), eq("org"))).thenReturn(List.of(table("DRONE", "LIVE")));
        var logs = LogCapture.attach(InfluxDbProjectionAdapter.class);

        assertThat(adapter.queryRecent(10)).singleElement()
                .satisfies(r -> assertThat(r.entityType()).isEqualTo(MonitoredEntityType.DRONE));
        assertThat(logs.warnings()).isEmpty();
        logs.detach();
    }
}
