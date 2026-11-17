package com.upmarket.catalogo.domain.exception;

/** El comprador ya calificó a este vendedor y la reseña es definitiva. */
public class ResenaDuplicadaException extends DuplicadoException {

    public ResenaDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
