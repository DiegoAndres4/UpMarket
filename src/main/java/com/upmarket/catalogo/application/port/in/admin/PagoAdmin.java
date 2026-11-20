package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.EstadoPago;
import java.math.BigDecimal;
import java.time.Instant;

/** Fila del listado de pagos del panel, con su publicación, comprador y recibo. */
public record PagoAdmin(long id, String referencia, BigDecimal monto, EstadoPago estado, Instant creadoEn,
                        Instant aprobadoEn, ResumenPublicacion publicacion, ResumenComprador comprador,
                        ResumenRecibo recibo) {

    public record ResumenPublicacion(long id, String titulo) {
    }

    public record ResumenComprador(String usuario, String nombre) {
    }

    public record ResumenRecibo(boolean existe, Long documentoId, String nombreArchivo, String numero,
                                Instant generadoEn) {
    }
}
