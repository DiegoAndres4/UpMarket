package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacionSpringJpaRepository extends JpaRepository<PublicacionJpaEntity, Long> {

    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE (:patronTexto IS NULL OR p.textoBusqueda LIKE :patronTexto ESCAPE '\\')
              AND (:categoria IS NULL OR p.categoria = :categoria)
              AND (:precioMin IS NULL OR p.precio >= :precioMin)
              AND (:precioMax IS NULL OR p.precio <= :precioMax)
              AND (:estadoArticulo IS NULL OR p.estadoArticulo = :estadoArticulo)
              AND (:estado IS NULL OR p.estado = :estado)
              AND (:vendedor IS NULL OR p.vendedor = :vendedor)
            ORDER BY p.publicadaEn DESC, p.id DESC
            """)
    List<PublicacionJpaEntity> buscar(
            @Param("patronTexto") String patronTexto,
            @Param("categoria") String categoria,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("estadoArticulo") EstadoArticulo estadoArticulo,
            @Param("estado") EstadoPublicacion estado,
            @Param("vendedor") String vendedor);

    @Query("""
            SELECT p.categoria, COUNT(p)
            FROM PublicacionJpaEntity p
            WHERE p.estado = com.upmarket.catalogo.domain.model.EstadoPublicacion.DISPONIBLE
            GROUP BY p.categoria
            """)
    List<Object[]> contarDisponiblesPorCategoria();
}
