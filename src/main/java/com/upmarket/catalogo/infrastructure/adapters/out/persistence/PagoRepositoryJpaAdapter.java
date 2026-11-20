package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.domain.model.Pago;
import java.util.List;
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
    public Optional<Pago> buscarPorId(long id) {
        return jpa.findById(id).map(PagoJpaEntity::aDominio);
    }

    @Override
    public Optional<Pago> buscarPorPublicacion(long publicacionId) {
        return jpa.findFirstByPublicacionIdOrderByCreadoEnDesc(publicacionId)
                .map(PagoJpaEntity::aDominio);
    }

    @Override
    public List<Pago> todos() {
        return jpa.findAll().stream().map(PagoJpaEntity::aDominio).toList();
    }
}
