package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.ResenaRepositoryPort;
import com.upmarket.catalogo.domain.model.Resena;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Repositorio en memoria para probar la aplicación sin Spring ni base de datos. */
public class ResenaRepositoryEnMemoria implements ResenaRepositoryPort {

    private final List<Resena> datos = new ArrayList<>();
    private long secuencia;

    @Override
    public Resena guardar(Resena resena) {
        Resena guardada = new Resena(++secuencia, resena.vendedor(), resena.autor(),
                resena.estrellas(), resena.comentario(), resena.creadaEn());
        datos.add(guardada);
        return guardada;
    }

    @Override
    public boolean existe(String vendedor, String autor) {
        return datos.stream().anyMatch(r -> r.vendedor().equals(vendedor) && r.autor().equals(autor));
    }

    @Override
    public List<Resena> porVendedor(String vendedor) {
        return datos.stream()
                .filter(r -> r.vendedor().equals(vendedor))
                .sorted(Comparator.comparing(Resena::creadaEn).reversed())
                .toList();
    }

    public int total() {
        return datos.size();
    }
}
