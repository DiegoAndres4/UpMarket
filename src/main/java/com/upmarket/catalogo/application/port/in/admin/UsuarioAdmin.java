package com.upmarket.catalogo.application.port.in.admin;

/** Fila del listado de usuarios del panel de administración (solo lectura). */
public record UsuarioAdmin(String usuario, String nombre, String correo, String fotoUrl, boolean eliminada,
                           long publicaciones) {
}
