package com.upmarket.catalogo.domain.exception;

/** Raíz de los errores de negocio. No conoce HTTP: la capa REST decide cómo traducirlos. */
public class DominioException extends RuntimeException {

    public DominioException(String mensaje) {
        super(mensaje);
    }
}
