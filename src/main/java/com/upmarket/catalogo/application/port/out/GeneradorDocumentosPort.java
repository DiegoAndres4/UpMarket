package com.upmarket.catalogo.application.port.out;

/** Dibuja los documentos del panel (HTML/CSS a PDF y XML de estadísticas). */
public interface GeneradorDocumentosPort {

    /** Recibo de pago en PDF, una página A4. */
    byte[] reciboPago(DatosRecibo datos);
}
