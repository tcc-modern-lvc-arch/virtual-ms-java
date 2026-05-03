package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaJpaRepository extends JpaRepository<AreaEntity, String> {
    List<AreaEntity> findByActiveTrue();
}
