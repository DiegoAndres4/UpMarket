package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.InteresRepositoryPort;
import com.upmarket.catalogo.domain.model.Interesado;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Personas interesadas en memoria, con la misma deduplicación que la base de datos. */
public class InteresRepositoryEnMemoria implements InteresRepositoryPort {

    private final Map<Long, Map<String, Interesado>> filas = new HashMap<>();

    @Override
    public void registrar(long publicacionId, String usuario, Instant ahora) {
        Map<String, Interesado> porUsuario = filas.computeIfAbsent(publicacionId, k -> new LinkedHashMap<>());
        porUsuario.merge(usuario, new Interesado(usuario, 1, ahora),
                (anterior, nueva) -> new Interesado(usuario, anterior.veces() + 1, ahora));
    }

    @Override
    public List<Interesado> listar(long publicacionId) {
        return filas.getOrDefault(publicacionId, Map.of()).values().stream()
                .sorted(Comparator.comparing(Interesado::ultimaVez).reversed())
                .toList();
    }

    @Override
    public void eliminarPorPublicacion(long publicacionId) {
        filas.remove(publicacionId);
    }

    public int total(long publicacionId) {
        return filas.getOrDefault(publicacionId, Map.of()).size();
    }
}
