package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Precio en pesos colombianos: mayor que cero, con hasta dos decimales. Objeto de valor: se crea ya validado. */
public final class Precio {

    private static final BigDecimal MAXIMO = new BigDecimal("999999999.99");

    private Precio() { }

    /** Convierte el texto a un valor válido; cualquier cosa que no sea un número mayor que cero es un error de validación. */
    public static BigDecimal de(String texto) {
        BigDecimal v = leer(texto);
        if (v == null || v.signum() <= 0 || v.compareTo(MAXIMO) > 0) {
            throw new ValorInvalidoException("El precio debe ser un número mayor que cero");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Variante para los filtros de búsqueda: acepta cero, pero no negativos ni letras. */
    public static BigDecimal filtro(String texto, String nombre) {
        BigDecimal v = leer(texto);
        if (v == null || v.signum() < 0) {
            throw new ValorInvalidoException("El " + nombre + " debe ser un número mayor o igual a cero");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal leer(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
