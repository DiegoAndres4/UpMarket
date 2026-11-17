package com.upmarket.catalogo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Resena;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResenaTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T12:00:00Z");

    private static Resena crear(int estrellas, String comentario) {
        return Resena.crear("maria@correo.com", "jose@correo.com", estrellas, comentario, AHORA);
    }

    @Test
    @DisplayName("Acepta los extremos del rango: 0 y 5 estrellas")
    void aceptaExtremosDelRango() {
        assertEquals(0, crear(0, "Muy malo").estrellas());
        assertEquals(5, crear(5, "Excelente").estrellas());
    }

    @Test
    @DisplayName("Rechaza estrellas fuera de 0 a 5")
    void rechazaFueraDeRango() {
        assertThrows(ValorInvalidoException.class, () -> crear(-1, "Comentario válido"));
        assertThrows(ValorInvalidoException.class, () -> crear(6, "Comentario válido"));
    }

    @Test
    @DisplayName("El comentario es obligatorio y va de 3 a 500 caracteres")
    void validaComentario() {
        assertEquals("abc", crear(4, "abc").comentario());
        assertEquals(500, crear(4, "a".repeat(500)).comentario().length());
        assertThrows(ValorInvalidoException.class, () -> crear(4, "ab"));
        assertThrows(ValorInvalidoException.class, () -> crear(4, "a".repeat(501)));
        assertThrows(ValorInvalidoException.class, () -> crear(4, "   "));
    }

    @Test
    @DisplayName("Nadie puede calificarse a sí mismo, sin importar mayúsculas")
    void rechazaAutoResena() {
        assertThrows(ValorInvalidoException.class,
                () -> Resena.crear("Jose@Correo.com", "jose@correo.com", 5, "Yo mismo", AHORA));
    }

    @Test
    @DisplayName("El vendedor es obligatorio")
    void exigeVendedor() {
        assertThrows(ValorInvalidoException.class,
                () -> Resena.crear("  ", "jose@correo.com", 5, "Comentario", AHORA));
    }
}
