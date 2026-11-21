package com.upmarket.catalogo.infrastructure.perfil;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Historial de perfiles archivados al eliminar una cuenta. Solo se inserta y se consulta. */
public interface PerfilEliminadoRepository extends JpaRepository<PerfilEliminado, Long> {

    /**
     * ¿La cuenta se archivó después de que se emitiera el token? Entonces el token es de la cuenta
     * anterior y debe dejar de servir. Un token emitido tras la baja (cuenta nueva con el mismo
     * usuario) no se bloquea.
     */
    boolean existsByUsuarioAndEliminadoEnAfter(String usuario, Instant emitidoEn);

    Optional<PerfilEliminado> findFirstByUsuarioOrderByEliminadoEnDesc(String usuario);
}
