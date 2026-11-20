package com.upmarket.catalogo.domain.exception;

public class DocumentoNoEncontradoException extends NoEncontradoException {
    public DocumentoNoEncontradoException(long id) {
        super("No existe el documento " + id);
    }
}
