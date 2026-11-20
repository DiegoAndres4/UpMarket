package com.upmarket.catalogo.application.port.in.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Cifras del tablero de administración. */
public record EstadisticasAdmin(long totalPublicaciones, Map<String, Long> publicacionesPorEstado,
                                Map<String, Long> publicacionesPorEstadoPago, long pagosAprobados,
                                BigDecimal ingresosAprobados, long vistasTotales,
                                List<VistaPublicacion> topPublicaciones, Map<String, Long> publicacionesPorCategoria,
                                List<ConteoDia> publicacionesPorDia, long totalPerfiles, long totalResenas) {

    /** Publicación con su número de vistas, para el ranking del tablero y del reporte. */
    public record VistaPublicacion(long id, String titulo, String categoria, long vistas) {
    }

    /** Publicaciones creadas en un día (fecha ISO, zona horaria del negocio). */
    public record ConteoDia(String fecha, long total) {
    }
}
