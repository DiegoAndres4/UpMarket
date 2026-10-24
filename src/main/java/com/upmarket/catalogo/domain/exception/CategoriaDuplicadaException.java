package com.upmarket.catalogo.domain.exception;

public class CategoriaDuplicadaException extends DuplicadoException {
    public CategoriaDuplicadaException(String nombre) {
        super("La categoría ya existe: " + nombre);
    }
}
