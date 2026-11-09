package com.upmarket.catalogo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Contacto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El celular debe ser colombiano y estar completo: 10 dígitos que empiezan por 3, con 57 o +57 opcional. */
class ContactoTest {

    @Test
    @DisplayName("Acepta celulares colombianos completos, con o sin indicativo")
    void aceptaCelularesColombianos() {
        assertEquals("3001234567", new Contacto("300 123 4567", null).whatsapp());
        assertEquals("+573001234567", new Contacto("+57 300-123-4567", null).whatsapp());
        assertEquals("573151234567", new Contacto("573151234567", null).whatsapp());
    }

    @Test
    @DisplayName("Rechaza números incompletos, de otro país o que no son celular")
    void rechazaNumerosInvalidos() {
        for (String malo : new String[]{"300123456", "30012345678", "2001234567", "+14155552671", "abc3001234567", "6015551234"}) {
            assertThrows(ValorInvalidoException.class, () -> new Contacto(malo, null), malo);
        }
    }

    @Test
    @DisplayName("Con solo correo no se exige celular")
    void soloCorreo() {
        assertEquals(null, new Contacto(null, "ana@correo.com").whatsapp());
    }

    @Test
    @DisplayName("Un contacto ya guardado con una regla anterior se puede reconstruir sin error")
    void rehidrataDatosAntiguos() {
        var viejo = Contacto.rehidratar("123456789", null);
        assertEquals("123456789", viejo.whatsapp());
    }
}
