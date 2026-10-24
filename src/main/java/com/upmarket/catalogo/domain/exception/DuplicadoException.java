package com.upmarket.catalogo.domain.exception;

/** Se intentó crear algo cuyo identificador de negocio ya existe. */
public abstract class DuplicadoException extends DominioException {

    protected DuplicadoException(String mensaje) {
        super(mensaje);
    }
}
