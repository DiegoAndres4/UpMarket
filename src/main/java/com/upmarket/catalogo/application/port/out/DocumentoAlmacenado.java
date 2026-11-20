package com.upmarket.catalogo.application.port.out;

/** Resultado de guardar un documento en el almacén privado. */
public record DocumentoAlmacenado(String proveedor, String referencia, String nombreArchivo, String contentType,
                                  long tamanoBytes) {
}
