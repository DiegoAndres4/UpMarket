package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PublicacionRepositoryPort {

    Publicacion guardar(Publicacion publicacion);

    Optional<Publicacion> buscarPorId(long id);

    /**
     * Igual que {@link #buscarPorId}, pero bloquea la fila para escritura hasta el fin de la
     * transacción. Es la base del cobro único: dos pagos simultáneos se serializan y el segundo ve
     * la publicación ya pagada.
     */
    Optional<Publicacion> buscarPorIdBloqueando(long id);

    void eliminar(long id);

    List<Publicacion> buscar(FiltroPublicacion filtro);

    /** Incremento atómico del contador de vistas; no debe ser leer-modificar-escribir. */
    void incrementarVistas(long id);

    Map<String, Long> contarDisponiblesPorCategoria();
}
