package com.upmarket.catalogo.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** Datos ya resueltos para dibujar el recibo de pago. */
public record DatosRecibo(long pagoId, String numero, String publicacionTitulo, String compradorNombre,
                          String compradorCorreo, BigDecimal monto, String referencia, Instant aprobadoEn,
                          Instant generadoEn) {
}
