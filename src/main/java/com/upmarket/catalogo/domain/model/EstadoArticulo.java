package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.Locale;

/** Condición del artículo: nuevo o usado. */
public enum EstadoArticulo {
    NUEVO, USADO;

    public static EstadoArticulo de(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ValorInvalidoException("El estado del artículo es obligatorio (NUEVO o USADO)");
        }
        String limpio = texto.trim().toUpperCase(Locale.ROOT);
        for (EstadoArticulo e : values()) {
            if (e.name().equals(limpio)) return e;
        }
        throw new ValorInvalidoException("Estado de artículo desconocido: " + texto + " (use NUEVO o USADO)");
    }
}
