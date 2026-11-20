package com.upmarket.catalogo.domain.model;

import java.time.Instant;

/**
 * Documento generado por el administrador (recibo de pago o reporte de estadísticas) y su ubicación
 * en el almacén privado. La referencia es opaca para el dominio: el adaptador la interpreta.
 */
public record DocumentoGenerado(Long id, TipoDocumento tipo, FormatoDocumento formato, String numero,
                                Long pagoId, String proveedor, String referencia, String nombreArchivo,
                                long tamanoBytes, Instant generadoEn, String generadoPor) {

    /** Documento recién reservado: sin archivo todavía ni número (el número depende del id). */
    public static DocumentoGenerado nuevo(TipoDocumento tipo, FormatoDocumento formato, Long pagoId,
                                          String generadoPor, Instant generadoEn) {
        return new DocumentoGenerado(null, tipo, formato, null, pagoId, null, null, null, 0L, generadoEn,
                generadoPor);
    }

    public DocumentoGenerado conNumero(String numero) {
        return new DocumentoGenerado(id, tipo, formato, numero, pagoId, proveedor, referencia, nombreArchivo,
                tamanoBytes, generadoEn, generadoPor);
    }

    public DocumentoGenerado conAlmacenamiento(String proveedor, String referencia, String nombreArchivo,
                                               long tamanoBytes) {
        return new DocumentoGenerado(id, tipo, formato, numero, pagoId, proveedor, referencia, nombreArchivo,
                tamanoBytes, generadoEn, generadoPor);
    }

    public boolean almacenado() {
        return referencia != null && !referencia.isBlank();
    }
}
