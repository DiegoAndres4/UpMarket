package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoSpringJpaRepository extends JpaRepository<PagoJpaEntity, Long> {

    Optional<PagoJpaEntity> findFirstByPublicacionIdOrderByCreadoEnDesc(Long publicacionId);
}
