package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.regex.Pattern;

/** Cómo contactar al vendedor: WhatsApp y/o correo (al menos uno). */
public record Contacto(String whatsapp, String correo) {

    private static final Pattern TELEFONO = Pattern.compile("\\+?[0-9]{7,15}");
    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Contacto {
        whatsapp = whatsapp == null || whatsapp.isBlank() ? null : whatsapp.trim().replaceAll("[\\s-]", "");
        correo = correo == null || correo.isBlank() ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
        if (whatsapp == null && correo == null) {
            throw new ValorInvalidoException("Indique al menos un medio de contacto: WhatsApp o correo");
        }
        if (whatsapp != null && !TELEFONO.matcher(whatsapp).matches()) {
            throw new ValorInvalidoException("El WhatsApp debe tener entre 7 y 15 dígitos, con + opcional");
        }
        if (correo != null && (correo.length() > 120 || !CORREO.matcher(correo).matches())) {
            throw new ValorInvalidoException("El correo de contacto no tiene un formato válido");
        }
    }
}
