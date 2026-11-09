package com.upmarket.catalogo.domain.exception;

public class FotoNoEncontradaException extends NoEncontradoException {
    public FotoNoEncontradaException() {
        super("Foto no encontrada");
    }
}
