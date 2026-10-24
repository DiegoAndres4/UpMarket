package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.List;
import java.util.Locale;

/**
 * Foto de una publicación. El servicio no guarda los bytes: guarda dónde está el archivo (url) y sus datos,
 * y valida las reglas de negocio: JPG o PNG, hasta 5 MB.
 */
public record Foto(String url, String tipo, long tamanoBytes) {

    public static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    public static final int MAXIMO_POR_PUBLICACION = 5;
    private static final List<String> TIPOS = List.of("image/jpeg", "image/png");

    public Foto {
        if (url == null || url.isBlank() || url.trim().length() > 500) {
            throw new ValorInvalidoException("La dirección de la foto es obligatoria (hasta 500 caracteres)");
        }
        String t = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(t)) {
            throw new ValorInvalidoException(mensajeDeLimites());
        }
        if (tamanoBytes <= 0 || tamanoBytes > MAXIMO_BYTES) {
            throw new ValorInvalidoException(mensajeDeLimites());
        }
        url = url.trim();
        tipo = t;
    }

    public static String mensajeDeLimites() {
        return "Foto no permitida: solo JPG o PNG, de máximo 5 MB, y hasta " + MAXIMO_POR_PUBLICACION + " fotos por publicación";
    }
}
