package com.upmarket.catalogo.application.port.in.admin;

import java.net.URI;

/**
 * Cómo entregar un documento: {@code enlace} para proveedores remotos (URL firmada y temporal) o
 * {@code contenido} para el almacenamiento local, que solo se sirve por este backend.
 */
public record DescargaDocumento(URI enlace, byte[] contenido, String nombreArchivo, String contentType) {
}
