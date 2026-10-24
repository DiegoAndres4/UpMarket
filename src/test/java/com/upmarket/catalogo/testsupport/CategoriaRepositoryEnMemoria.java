package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.domain.model.Categoria;
import java.util.*;

public class CategoriaRepositoryEnMemoria implements CategoriaRepositoryPort {

    private final Map<Long, Categoria> filas = new TreeMap<>();
    private long siguiente = 1;

    @Override
    public Categoria guardar(Categoria c) {
        long id = c.getId() == null ? siguiente++ : c.getId();
        Categoria copia = Categoria.reconstruir(id, c.getNombre(), c.getClave());
        filas.put(id, copia);
        return copia;
    }

    @Override
    public Optional<Categoria> buscarPorClave(String clave) {
        return filas.values().stream().filter(c -> c.getClave().equals(clave)).findFirst();
    }

    @Override
    public List<Categoria> listar() {
        List<Categoria> r = new ArrayList<>(filas.values());
        r.sort(Comparator.comparing(Categoria::getClave));
        return r;
    }
}
