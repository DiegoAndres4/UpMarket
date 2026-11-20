package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import java.time.Instant;

/** Documento generado tal como lo ve el panel de administración. */
public record DocumentoAdmin(long id, TipoDocumento tipo, FormatoDocumento formato, String numero,
                             String nombreArchivo, long tamanoBytes, Instant generadoEn, String generadoPor) {

    public static DocumentoAdmin de(DocumentoGenerado documento) {
        return new DocumentoAdmin(documento.id(), documento.tipo(), documento.formato(), documento.numero(),
                documento.nombreArchivo(), documento.tamanoBytes(), documento.generadoEn(),
                documento.generadoPor());
    }
}
