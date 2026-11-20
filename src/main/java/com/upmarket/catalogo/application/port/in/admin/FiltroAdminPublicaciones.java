package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;

/** Filtros y paginación del listado de publicaciones del panel de administración. */
public record FiltroAdminPublicaciones(String texto, String categoria, EstadoPublicacion estadoPublicacion,
                                       EstadoPago estadoPago, String vendedor, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    public FiltroAdminPublicaciones {
        pagina = Math.max(pagina, 1);
        tamano = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
    }
}
