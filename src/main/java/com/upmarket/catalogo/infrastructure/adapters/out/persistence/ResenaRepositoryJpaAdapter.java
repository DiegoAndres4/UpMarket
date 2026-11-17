package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.ResenaRepositoryPort;
import com.upmarket.catalogo.domain.model.Resena;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ResenaRepositoryJpaAdapter implements ResenaRepositoryPort {

    private final ResenaSpringJpaRepository jpa;

    public ResenaRepositoryJpaAdapter(ResenaSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Resena guardar(Resena resena) {
        return jpa.saveAndFlush(new ResenaJpaEntity(resena)).aDominio();
    }

    @Override
    public boolean existe(String vendedor, String autor) {
        return jpa.existsByVendedorAndAutor(vendedor, autor);
    }

    @Override
    public List<Resena> porVendedor(String vendedor) {
        return jpa.findByVendedorOrderByCreadaEnDesc(vendedor).stream()
                .map(ResenaJpaEntity::aDominio)
                .toList();
    }
}
