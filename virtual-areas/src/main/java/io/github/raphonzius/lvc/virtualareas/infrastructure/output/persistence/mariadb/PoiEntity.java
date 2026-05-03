package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "points_of_interest")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoiEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "poi_type", nullable = false)
    private PoiTypeJpa poiType;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PoiTypeJpa {BUS_STOP, MISSION_TARGET, GENERIC}
}
