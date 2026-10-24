package com.upmarket.catalogo.domain.exception;

/** La operación choca con el estado actual: publicación ya vendida (no se edita ni se contacta), vendida dos veces... (409). */
public class EstadoInvalidoException extends DominioException {
    public EstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
