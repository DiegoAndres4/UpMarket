package com.upmarket.catalogo.application.port.in.admin;

import java.time.Instant;

/** Fila del historial de reportes: un número con sus descargas PDF y XML. */
public record ReporteAdmin(String numero, Instant generadoEn, String generadoPor, DocumentoAdmin pdf,
                           DocumentoAdmin xml) {
}
