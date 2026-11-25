package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.time.Instant;

/**
 * Persona que pidió el contacto de una publicación. Hay una fila por persona y publicación; si vuelve
 * a pedir el contacto, se actualizan la fecha y el contador, no se duplica.
 */
public record Interesado(String usuario, int veces, Instant ultimaVez) {

    public static final int MAX_USUARIO = 120;

    public Interesado {
        usuario = usuario == null ? "" : usuario.trim();
        if (usuario.isEmpty() || usuario.length() > MAX_USUARIO) {
            throw new ValorInvalidoException("El usuario interesado es obligatorio");
        }
        if (veces < 1) {
            veces = 1;
        }
        if (ultimaVez == null) {
            throw new ValorInvalidoException("La fecha de interés es obligatoria");
        }
    }
}
