package com.upmarket.catalogo.domain.model;

import java.util.regex.Pattern;

/** Celular colombiano: 10 dígitos que empiezan por 3, con indicativo 57 o +57 opcional. Sin frameworks. */
public final class TelefonoColombia {

    public static final String MENSAJE =
            "El celular debe ser colombiano: 10 dígitos que empiecen por 3 (ej. 300 123 4567), con +57 opcional";

    private static final Pattern MOVIL = Pattern.compile("(\\+?57)?3[0-9]{9}");

    private TelefonoColombia() { }

    /** Quita espacios y guiones; devuelve null si viene vacío. */
    public static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim().replaceAll("[\\s-]", "");
    }

    public static boolean esValido(String limpio) {
        return limpio != null && MOVIL.matcher(limpio).matches();
    }
}
