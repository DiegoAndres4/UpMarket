package com.upmarket.catalogo.domain.exception;

/**
 * El almacén privado de documentos no pudo completar una operación (por ejemplo, borrar un archivo).
 * No es un fallo del negocio sino de la dependencia externa: la capa REST lo traduce a 502.
 */
public class AlmacenDocumentosException extends DominioException {

    public AlmacenDocumentosException(String mensaje) {
        super(mensaje);
    }
}
