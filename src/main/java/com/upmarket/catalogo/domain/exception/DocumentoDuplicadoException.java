package com.upmarket.catalogo.domain.exception;

/** Ya existe un documento con la misma clave lógica (por ejemplo, el recibo de un pago). */
public class DocumentoDuplicadoException extends DuplicadoException {
    public DocumentoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
