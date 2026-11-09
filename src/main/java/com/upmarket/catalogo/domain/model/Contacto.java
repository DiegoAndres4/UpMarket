package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.regex.Pattern;

/** Cómo contactar al vendedor: WhatsApp y/o correo (al menos uno). */
public record Contacto(String whatsapp, String correo) {

    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Contacto {
        whatsapp = TelefonoColombia.limpiar(whatsapp);
        correo = correo == null || correo.isBlank() ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
        if (whatsapp == null && correo == null) {
            throw new ValorInvalidoException("Indique al menos un medio de contacto: WhatsApp o correo");
        }
        if (whatsapp != null && !TelefonoColombia.esValido(whatsapp)) {
            throw new ValorInvalidoException(TelefonoColombia.MENSAJE);
        }
        if (correo != null && (correo.length() > 120 || !CORREO.matcher(correo).matches())) {
            throw new ValorInvalidoException("El correo de contacto no tiene un formato válido");
        }
    }
}
