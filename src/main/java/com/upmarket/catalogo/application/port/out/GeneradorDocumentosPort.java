package com.upmarket.catalogo.application.port.out;

/** Dibuja los documentos del panel (HTML/CSS a PDF y XML de estadísticas). */
public interface GeneradorDocumentosPort {

    /** Recibo de pago en PDF, una página A4. */
    byte[] reciboPago(DatosRecibo datos);

    /** Reporte de estadísticas en PDF, A4 y con varias páginas si hace falta. */
    byte[] reporteEstadisticasPdf(DatosReporte datos);

    /** El mismo reporte en XML (UTF-8, esquema propio). */
    byte[] reporteEstadisticasXml(DatosReporte datos);
}
