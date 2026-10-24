package com.upmarket.catalogo.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.domain.exception.AccesoDenegadoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.testsupport.Datos;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoliticaAccesoTest {

    private final PoliticaAcceso acceso = new PoliticaAcceso();

    @Test
    @DisplayName("Solo el ADMIN pasa las operaciones de administrador")
    void admin() {
        acceso.asegurarAdmin(Datos.admin(), "crear categorías");
        assertThrows(AccesoDenegadoException.class, () -> acceso.asegurarAdmin(Datos.estudiante("maria"), "crear categorías"));
    }

    @Test
    @DisplayName("Solo el autor modifica su publicación; el ADMIN tampoco")
    void autor() {
        Publicacion p = Datos.publicacion();
        assertTrue(acceso.esAutor(p, Datos.estudiante("maria")));
        acceso.asegurarEsAutor(p, Datos.estudiante("maria"));
        assertFalse(acceso.esAutor(p, Datos.estudiante("jose")));
        assertThrows(AccesoDenegadoException.class, () -> acceso.asegurarEsAutor(p, Datos.estudiante("jose")));
        assertThrows(AccesoDenegadoException.class, () -> acceso.asegurarEsAutor(p, Datos.admin()));
    }

    @Test
    @DisplayName("Actor: acepta ROLE_ADMIN, ignora roles desconocidos y exige usuario")
    void actor() {
        Actor a = Actor.de(" ana ", List.of("ROLE_ADMIN", "ROLE_OTRO"));
        assertEquals("ana", a.username());
        assertTrue(a.esAdmin());
        assertFalse(Actor.de("pepe", List.of("USER")).esAdmin());
        assertThrows(ValorInvalidoException.class, () -> Actor.de(" ", List.of("USER")));
    }
}
