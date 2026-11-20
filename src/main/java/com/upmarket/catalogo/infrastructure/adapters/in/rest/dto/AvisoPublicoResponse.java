package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.upmarket.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aviso tal como lo ve un visitante en la cartelera pública. A propósito NO incluye contacto, vendedor,
 * vistas ni datos de pago: solo lo necesario para mostrar la tarjeta del aviso.
 */
public record AvisoPublicoResponse(long id, String titulo, BigDecimal precio, String categoria,
                                   String estadoArticulo, String fotoUrl, Instant creadoEn) {

    public static AvisoPublicoResponse de(Publicacion p) {
        String fotoUrl = p.getFotos().isEmpty() ? null : p.getFotos().get(0).url();
        return new AvisoPublicoResponse(p.getId(), p.getTitulo(), p.getPrecio(), p.getCategoria(),
                p.getEstadoArticulo().name(), fotoUrl, p.getPublicadaEn());
    }
}
