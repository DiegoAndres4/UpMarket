package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.domain.exception.CategoriaDuplicadaException;
import com.upmarket.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class CategoriaRepositoryJpaAdapter implements CategoriaRepositoryPort {

    private final CategoriaSpringJpaRepository jpa;

    public CategoriaRepositoryJpaAdapter(CategoriaSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Categoria guardar(Categoria c) {
        try {
            CategoriaJpaEntity entidad = new CategoriaJpaEntity(c.getId(), c.getNombre(), c.getClave());
            return jpa.saveAndFlush(entidad).aDominio();
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaDuplicadaException(c.getNombre());
        }
    }

    @Override
    public Optional<Categoria> buscarPorClave(String clave) {
        return jpa.findByNombreNormalizado(clave).map(CategoriaJpaEntity::aDominio);
    }

    @Override
    public List<Categoria> listar() {
        return jpa.findAllByOrderByNombreNormalizadoAsc().stream().map(CategoriaJpaEntity::aDominio).toList();
    }
}
