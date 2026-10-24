package com.upmarket.catalogo.domain.exception;

public class PublicacionNoEncontradaException extends NoEncontradoException {
    public PublicacionNoEncontradaException(long id) {
        super("No existe la publicación " + id);
    }
}
