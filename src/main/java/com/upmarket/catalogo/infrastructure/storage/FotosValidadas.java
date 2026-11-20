package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Foto;

/**
 * Validación compartida por los adaptadores de almacenamiento: la imagen se valida por su
 * CONTENIDO (firma del archivo), nunca por el nombre ni por el Content-Type del cliente.
 */
final class FotosValidadas {

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";

    private FotosValidadas() {
    }

    /** Devuelve el tipo de la imagen o lanza {@link ValorInvalidoException} si no es JPG/PNG o excede el límite. */
    static String tipoDeContenido(byte[] contenido) {
        if (contenido == null || contenido.length == 0 || contenido.length > Foto.MAXIMO_BYTES) {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        if (esPng(contenido)) {
            return PNG;
        }
        if (esJpeg(contenido)) {
            return JPEG;
        }
        throw new ValorInvalidoException(Foto.mensajeDeLimites());
    }

    static String extensionDe(String tipo) {
        return PNG.equals(tipo) ? "png" : "jpg";
    }

    private static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean esJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
