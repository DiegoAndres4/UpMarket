package com.upmarket.catalogo.application.port.in.admin;

import java.util.List;

/** Página de resultados del panel de administración. */
public record PaginaAdmin<T>(List<T> contenido, int pagina, int tamano, long total) {

    public static <T> PaginaAdmin<T> de(List<T> todos, int pagina, int tamano) {
        int paginaSegura = Math.max(pagina, 1);
        int tamanoSeguro = tamano <= 0 ? FiltroAdminPublicaciones.TAMANO_POR_DEFECTO
                : Math.min(tamano, FiltroAdminPublicaciones.TAMANO_MAXIMO);
        long total = todos.size();
        long desde = Math.min((long) (paginaSegura - 1) * tamanoSeguro, total);
        long hasta = Math.min(desde + tamanoSeguro, total);
        List<T> contenido = todos.subList((int) desde, (int) hasta);
        return new PaginaAdmin<>(List.copyOf(contenido), paginaSegura, tamanoSeguro, total);
    }
}
