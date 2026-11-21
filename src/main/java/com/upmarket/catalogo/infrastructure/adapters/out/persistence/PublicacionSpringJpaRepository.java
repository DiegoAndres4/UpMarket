package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
              AND (:estadoPago IS NULL OR p.estadoPago = :estadoPago)
              AND p.archivadaEn IS NULL
            ORDER BY p.publicadaEn DESC, p.id DESC
            """)
    List<PublicacionJpaEntity> buscar(
            @Param("patronTexto") String patronTexto,
            @Param("categoria") String categoria,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("estadoArticulo") EstadoArticulo estadoArticulo,
            @Param("estado") EstadoPublicacion estado,
            @Param("vendedor") String vendedor,
            @Param("estadoPago") EstadoPago estadoPago);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PublicacionJpaEntity p SET p.vistas = p.vistas + 1 WHERE p.id = :id")
    int incrementarVistas(@Param("id") long id);

    @Query("""
            SELECT p.categoria, COUNT(p)
            FROM PublicacionJpaEntity p
            WHERE p.estado = com.upmarket.catalogo.domain.model.EstadoPublicacion.DISPONIBLE
              AND p.estadoPago = com.upmarket.catalogo.domain.model.EstadoPago.APROBADO
              AND p.archivadaEn IS NULL
            GROUP BY p.categoria
            """)
    List<Object[]> contarDisponiblesPorCategoria();
}
