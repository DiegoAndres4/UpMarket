package com.upmarket.catalogo.application.port.in.admin;

import java.time.Instant;

/** Resultado de generar un reporte: los dos archivos comparten número y fecha. */
public record ReporteGenerado(String numero, Instant generadoEn, String generadoPor, DocumentoAdmin pdf,
                              DocumentoAdmin xml) {
}
