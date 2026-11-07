package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PublicacionRepositoryPort {

    Publicacion guardar(Publicacion publicacion);

    Optional<Publicacion> buscarPorId(long id);

    void eliminar(long id);

    List<Publicacion> buscar(FiltroPublicacion filtro);

    Map<String, Long> contarDisponiblesPorCategoria();
}
