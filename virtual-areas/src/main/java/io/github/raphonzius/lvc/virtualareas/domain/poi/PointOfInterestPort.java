package io.github.raphonzius.lvc.virtualareas.domain.poi;

import java.util.List;
import java.util.Optional;

public interface PointOfInterestPort {
    PointOfInterest save(PointOfInterest poi);

    Optional<PointOfInterest> findById(String id);

    List<PointOfInterest> findAll();

    List<PointOfInterest> findActive();

    void delete(String id);
}
