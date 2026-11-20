package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.upmarket.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Vista pública de una publicación. A propósito NO incluye el contacto: se pide aparte (HU-11). */
public record PublicacionResponse(long id, String titulo, String descripcion, BigDecimal precio, String estadoArticulo,
                                  String categoria, List<FotoDto> fotos, String vendedor, Instant publicadaEn,
                                  String estadoPublicacion, Instant vendidaEn, String estadoPago,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Long vistas) {

    public static PublicacionResponse de(Publicacion p) {
        return de(p, null);
    }

    /**
     * Las vistas solo se incluyen para el dueño o un administrador; el resto de usuarios recibe {@code null}
     * y el campo se omite del JSON.
     */
    public static PublicacionResponse de(Publicacion p, Long vistas) {
        return new PublicacionResponse(p.getId(), p.getTitulo(), p.getDescripcion(), p.getPrecio(),
                p.getEstadoArticulo().name(), p.getCategoria(), p.getFotos().stream().map(FotoDto::de).toList(),
                p.getVendedor(), p.getPublicadaEn(), p.getEstado().name(), p.getVendidaEn(),
                p.getEstadoPago().name(), vistas);
    }
}
