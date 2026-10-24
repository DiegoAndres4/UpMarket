package com.upmarket.catalogo.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/** Comparación de texto «humana»: sin mayúsculas, sin tildes y con los espacios en orden. */
public final class Texto {

    private Texto() { }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
