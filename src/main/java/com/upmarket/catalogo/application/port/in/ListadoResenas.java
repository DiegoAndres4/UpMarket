package com.upmarket.catalogo.application.port.in;

import java.util.List;

/** Resumen y lista de reseñas de un vendedor, más recientes primero. */
public record ListadoResenas(String vendedor, double promedio, long total, List<ResenaVista> resenas) {
}
