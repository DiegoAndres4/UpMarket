package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaSpringJpaRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    Optional<CategoriaJpaEntity> findByNombreNormalizado(String nombreNormalizado);

    List<CategoriaJpaEntity> findAllByOrderByNombreNormalizadoAsc();
}
