package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.upmarket.catalogo.domain.model.Pago;
import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de un pago simulado de publicación. */
public record PagoResponse(long id, long publicacionId, BigDecimal monto, String estado, String referencia,
                           Instant creadoEn, Instant aprobadoEn) {

    public static PagoResponse de(Pago pago) {
        return new PagoResponse(pago.getId(), pago.getPublicacionId(), pago.getMonto(), pago.getEstado().name(),
                pago.getReferencia(), pago.getCreadoEn(), pago.getAprobadoEn());
    }
}
