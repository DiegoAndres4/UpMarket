package com.upmarket.catalogo.domain.exception;

/** El recurso pedido no existe. */
public abstract class NoEncontradoException extends DominioException {

    protected NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
