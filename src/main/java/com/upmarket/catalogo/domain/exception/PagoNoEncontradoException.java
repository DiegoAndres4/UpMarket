package com.upmarket.catalogo.domain.exception;

public class PagoNoEncontradoException extends NoEncontradoException {
    public PagoNoEncontradoException(long id) {
        super("No existe el pago " + id);
    }
}
