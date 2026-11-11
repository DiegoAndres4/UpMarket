package com.upmarket.catalogo.infrastructure.perfil;

/**
 * Puerto para hablar con AuthCore cuando se elimina una cuenta.
 * Vive en infraestructura: el dominio no sabe que existe otro servicio.
 */
public interface CredencialesPort {
    /** Borra las credenciales del usuario dueño del token. */
    void eliminarCuenta(String authorizationHeader);
}
