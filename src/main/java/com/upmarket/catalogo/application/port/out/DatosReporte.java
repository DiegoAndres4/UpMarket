package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin;
import java.time.Instant;
import java.util.List;

/** Una sola foto de las estadísticas, compartida por el PDF y el XML del mismo reporte. */
public record DatosReporte(EstadisticasAdmin estadisticas, String numero, String generadoPor,
                           Instant generadoEn, List<String> notas) {
}
