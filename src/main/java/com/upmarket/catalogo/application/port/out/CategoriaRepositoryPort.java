package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Optional;

public interface CategoriaRepositoryPort {

    Categoria guardar(Categoria categoria);

    Optional<Categoria> buscarPorClave(String clave);

    List<Categoria> listar();
}
