package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResenaSpringJpaRepository extends JpaRepository<ResenaJpaEntity, Long> {

    boolean existsByVendedorAndAutor(String vendedor, String autor);

    List<ResenaJpaEntity> findByVendedorOrderByCreadaEnDesc(String vendedor);
}
