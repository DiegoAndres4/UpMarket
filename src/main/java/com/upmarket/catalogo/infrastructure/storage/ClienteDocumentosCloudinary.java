package com.upmarket.catalogo.infrastructure.storage;

import java.io.IOException;
import java.util.Map;

/**
 * Envoltorio mínimo del SDK de Cloudinary para los documentos. Aísla al adaptador del SDK y permite
 * probarlo con un doble: se verifica que se suba como {@code raw}/{@code private} y que el enlace
 * de descarga lleve caducidad, sin red.
 */
public interface ClienteDocumentosCloudinary {

    Map<?, ?> subir(byte[] contenido, Map<String, Object> opciones) throws IOException;

    String enlacePrivado(String publicId, String formato, Map<String, Object> opciones) throws Exception;

    /** Destruye el archivo remoto; el SDK devuelve un mapa con {@code result=ok|not found|error}. */
    Map<?, ?> destruir(String publicId, Map<String, Object> opciones) throws IOException;

    /** Descarga el contenido de un enlace firmado, del lado del servidor. */
    byte[] descargar(String enlace) throws IOException;
}
