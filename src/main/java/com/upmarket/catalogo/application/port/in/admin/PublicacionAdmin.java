package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import com.upmarket.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.time.Instant;

/** Fila del listado de publicaciones del panel de administración, incluidas las pendientes de pago. */
public record PublicacionAdmin(long id, String titulo, String vendedor, String categoria, BigDecimal precio,
                               EstadoPublicacion estadoPublicacion, EstadoPago estadoPago, long vistas,
                               Instant publicadaEn, String fotoUrl) {

    public static PublicacionAdmin de(Publicacion publicacion) {
        String foto = publicacion.getFotos().isEmpty() ? null : publicacion.getFotos().get(0).url();
        return new PublicacionAdmin(publicacion.getId(), publicacion.getTitulo(), publicacion.getVendedor(),
                publicacion.getCategoria(), publicacion.getPrecio(), publicacion.getEstado(),
                publicacion.getEstadoPago(), publicacion.getVistas(), publicacion.getPublicadaEn(), foto);
    }
}
