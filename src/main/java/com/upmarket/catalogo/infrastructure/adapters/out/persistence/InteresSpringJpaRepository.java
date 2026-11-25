package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InteresSpringJpaRepository extends JpaRepository<InteresJpaEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InteresJpaEntity i
               SET i.veces = i.veces + 1,
                   i.ultimaVez = :ahora
             WHERE i.publicacionId = :publicacionId
               AND i.usuario = :usuario
            """)
    int registrar(@Param("publicacionId") long publicacionId, @Param("usuario") String usuario,
                  @Param("ahora") Instant ahora);

    List<InteresJpaEntity> findByPublicacionIdOrderByUltimaVezDesc(long publicacionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InteresJpaEntity i WHERE i.publicacionId = :publicacionId")
    int eliminarPorPublicacion(@Param("publicacionId") long publicacionId);
}
