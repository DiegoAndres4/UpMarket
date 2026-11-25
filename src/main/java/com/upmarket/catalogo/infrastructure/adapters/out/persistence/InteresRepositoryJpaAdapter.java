package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.InteresRepositoryPort;
import com.upmarket.catalogo.domain.model.Interesado;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InteresRepositoryJpaAdapter implements InteresRepositoryPort {

    private final InteresSpringJpaRepository jpa;

    public InteresRepositoryJpaAdapter(InteresSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public synchronized void registrar(long publicacionId, String usuario, Instant ahora) {
        if (jpa.registrar(publicacionId, usuario, ahora) == 0) {
            jpa.saveAndFlush(new InteresJpaEntity(publicacionId, usuario, ahora));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Interesado> listar(long publicacionId) {
        return jpa.findByPublicacionIdOrderByUltimaVezDesc(publicacionId).stream()
                .map(InteresJpaEntity::aDominio)
                .toList();
    }

    @Override
    @Transactional
    public void eliminarPorPublicacion(long publicacionId) {
        jpa.eliminarPorPublicacion(publicacionId);
    }
}
