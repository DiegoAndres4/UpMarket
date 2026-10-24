package com.upmarket.catalogo.domain.exception;

public class CategoriaNoEncontradaException extends NoEncontradoException {
    public CategoriaNoEncontradaException(String nombre) {
        super("No existe la categoría " + nombre);
    }
}
