package com.upmarket.catalogo.application.port.out;

import java.net.URI;
import java.time.Duration;

/**
 * Almacén de documentos generados (recibos y reportes). A diferencia de las fotos, estos archivos
 * contienen datos personales y de negocio: se guardan PRIVADOS y solo se sirven por el endpoint de
 * descarga del administrador. El adaptador decide dónde viven (disco local o Cloudinary).
 */
public interface AlmacenDocumentosPort {

    /** Carpeta remota de los recibos de pago. */
    String CARPETA_RECIBOS = "upmarket/recibos";

    /** Carpeta remota de los reportes de estadísticas. */
    String CARPETA_REPORTES = "upmarket/reportes";

    /** Guarda el contenido y devuelve una referencia opaca (nunca una URL pública permanente). */
    DocumentoAlmacenado guardar(byte[] contenido, String nombreArchivo, String carpeta, String contentType);

    /**
     * Enlace firmado y temporal para proveedores remotos. Devuelve {@code null} cuando el archivo se
     * sirve por streaming desde el propio backend (almacenamiento local).
     */
    URI enlaceDescarga(DocumentoAlmacenado documento, Duration validez);

    /**
     * Lee el contenido del documento. El almacenamiento local lo abre del disco; el remoto lo
     * descarga con un enlace firmado de corta caducidad, siempre del lado del servidor.
     */
    byte[] leer(DocumentoAlmacenado documento);
}
