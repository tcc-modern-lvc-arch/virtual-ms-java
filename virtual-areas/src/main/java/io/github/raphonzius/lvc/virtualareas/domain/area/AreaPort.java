package io.github.raphonzius.lvc.virtualareas.domain.area;

import java.util.List;
import java.util.Optional;

public interface AreaPort {
    Area save(Area area);

    Optional<Area> findById(String id);

    List<Area> findAll();

    List<Area> findActive();

    void delete(String id);
}
