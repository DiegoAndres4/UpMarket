package com.upmarket.catalogo.application.port.out;

/**
 * Fila de perfil para el administrador. El correo es el nombre de usuario que entrega AuthCore.
 */
public record PerfilAdmin(String usuario, String nombre, String correo, String fotoUrl, boolean eliminada) {
}
