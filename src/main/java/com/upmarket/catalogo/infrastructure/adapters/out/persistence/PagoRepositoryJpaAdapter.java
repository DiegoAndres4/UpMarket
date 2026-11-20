package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.domain.model.Pago;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PagoRepositoryJpaAdapter implements PagoRepositoryPort {

    private final PagoSpringJpaRepository jpa;

    public PagoRepositoryJpaAdapter(PagoSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pago guardar(Pago pago) {
        return jpa.saveAndFlush(new PagoJpaEntity(pago)).aDominio();
    }

    @Override
    public Optional<Pago> buscarPorPublicacion(long publicacionId) {
        return jpa.findFirstByPublicacionIdOrderByCreadoEnDesc(publicacionId)
                .map(PagoJpaEntity::aDominio);
    }
}
