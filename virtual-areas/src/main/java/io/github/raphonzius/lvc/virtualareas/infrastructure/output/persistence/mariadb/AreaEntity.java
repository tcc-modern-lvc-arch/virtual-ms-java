package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "areas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AreaEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_type", nullable = false)
    private AreaTypeJpa areaType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON", nullable = false)
    private List<CoordinateJson> coordinates;

    @Column(name = "radius_meters")
    private Double radiusMeters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "monitored_entity_types", columnDefinition = "JSON", nullable = false)
    private List<String> monitoredEntityTypes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaActionJpa action;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "target_poi_id")
    private String targetPoiId;

    @Column(name = "patrol_zone", nullable = false)
    private boolean patrolZone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public enum AreaTypeJpa {POLYGON, CIRCLE, CORRIDOR}

    public enum AreaActionJpa {CHECKIN_CHECKOUT, MISSION_TRIGGER}
}
