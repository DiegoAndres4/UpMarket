package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Interesado;
import java.time.Instant;
import java.util.List;

/** Personas que pidieron el contacto de una publicación. */
public interface InteresRepositoryPort {

    /** Registra una solicitud: crea la fila o actualiza fecha y contador si la persona ya estaba. */
    void registrar(long publicacionId, String usuario, Instant ahora);

    /** Personas interesadas, la más reciente primero. */
    List<Interesado> listar(long publicacionId);

    /** Limpia el historial cuando la publicación se elimina de verdad. */
    void eliminarPorPublicacion(long publicacionId);
}
