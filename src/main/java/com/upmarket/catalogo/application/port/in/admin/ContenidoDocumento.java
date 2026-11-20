package com.upmarket.catalogo.application.port.in.admin;

/**
 * Bytes de un documento para verlo en línea. A diferencia de la descarga remota, aquí el backend
 * siempre entrega el contenido: si el archivo vive en Cloudinary, lo trae por el enlace firmado.
 */
public record ContenidoDocumento(byte[] contenido, String nombreArchivo, String contentType) {
}
