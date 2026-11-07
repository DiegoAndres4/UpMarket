package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import java.math.BigDecimal;

public record FiltroPublicacion(
        String texto,
        String categoria,
        BigDecimal precioMin,
        BigDecimal precioMax,
        EstadoArticulo estadoArticulo,
        EstadoPublicacion estado,
        String vendedor) {
}
