package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cómo contactar al vendedor: celular colombiano y/o correo (al menos uno).
 * El constructor público valida con las reglas actuales (se usa al publicar). {@link #rehidratar} reconstruye un
 * contacto ya guardado SIN volver a validar el celular: así, datos escritos con una regla anterior no impiden leer
 * ni borrar la publicación.
 */
public final class Contacto {

    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final String whatsapp;
    private final String correo;

    public Contacto(String whatsapp, String correo) {
        this(whatsapp, correo, true);
    }

    private Contacto(String whatsapp, String correo, boolean validar) {
        this.whatsapp = TelefonoColombia.limpiar(whatsapp);
        this.correo = correo == null || correo.isBlank() ? null : correo.trim().toLowerCase(Locale.ROOT);
        if (!validar) {
            return;
        }
        if (this.whatsapp == null && this.correo == null) {
            throw new ValorInvalidoException("Indique al menos un medio de contacto: WhatsApp o correo");
        }
        if (this.whatsapp != null && !TelefonoColombia.esValido(this.whatsapp)) {
            throw new ValorInvalidoException(TelefonoColombia.MENSAJE);
        }
        if (this.correo != null && (this.correo.length() > 120 || !CORREO.matcher(this.correo).matches())) {
            throw new ValorInvalidoException("El correo de contacto no tiene un formato válido");
        }
    }

    /** Reconstruye un contacto que ya estaba guardado (no valida: lo que se guardó, se puede leer). */
    public static Contacto rehidratar(String whatsapp, String correo) {
        return new Contacto(whatsapp, correo, false);
    }

    public String whatsapp() { return whatsapp; }

    public String correo() { return correo; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Contacto c && Objects.equals(whatsapp, c.whatsapp) && Objects.equals(correo, c.correo);
    }

    @Override
    public int hashCode() { return Objects.hash(whatsapp, correo); }

    @Override
    public String toString() { return "Contacto[whatsapp=" + whatsapp + ", correo=" + correo + "]"; }
}
