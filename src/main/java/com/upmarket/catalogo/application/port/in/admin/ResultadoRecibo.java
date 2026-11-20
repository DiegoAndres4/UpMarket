package com.upmarket.catalogo.application.port.in.admin;

/** Resultado de pedir el recibo de un pago: {@code nuevo} decide entre 201 y 200. */
public record ResultadoRecibo(boolean nuevo, DocumentoAdmin documento) {
}
