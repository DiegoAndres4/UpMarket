package com.upmarket.catalogo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upmarket.catalogo.application.port.in.ListadoResenas;
import com.upmarket.catalogo.application.port.in.ResenaUseCase;
import com.upmarket.catalogo.application.port.in.command.CrearResenaCommand;
import com.upmarket.catalogo.application.service.ResenaService;
import com.upmarket.catalogo.domain.exception.ResenaDuplicadaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.testsupport.Datos;
import com.upmarket.catalogo.testsupport.PerfilPublicoEnMemoria;
import com.upmarket.catalogo.testsupport.RelojAjustable;
import com.upmarket.catalogo.testsupport.ResenaRepositoryEnMemoria;
import com.upmarket.catalogo.testsupport.TransaccionDirecta;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResenaServiceTest {

    private final ResenaRepositoryEnMemoria resenas = new ResenaRepositoryEnMemoria();
    private final RelojAjustable reloj = Datos.relojAjustable();
    private final PerfilPublicoEnMemoria perfiles = new PerfilPublicoEnMemoria(
            Map.of("jose@correo.com", "José"));
    private final ResenaUseCase servicio = new ResenaService(
            resenas, new TransaccionDirecta(), reloj, perfiles);

    private static final Actor JOSE = Actor.de("jose@correo.com", List.of("USER"));
    private static final Actor ANA = Actor.de("ana@correo.com", List.of("USER"));

    @Test
    @DisplayName("Crea reseñas, promedia y trae el nombre visible del autor")
    void creaYListaConPromedioYNombreVisible() {
        servicio.crear(JOSE, "maria@correo.com", new CrearResenaCommand(5, "Excelente vendedora"));
        reloj.avanzar(Duration.ofMinutes(1));
        servicio.crear(ANA, "maria@correo.com", new CrearResenaCommand(3, "Regular"));

        ListadoResenas listado = servicio.listar("maria@correo.com");

        assertEquals(4.0, listado.promedio());
        assertEquals(2, listado.total());
        assertEquals("ana@correo.com", listado.resenas().get(0).autor());
        assertEquals("José", listado.resenas().get(1).autorNombre());
    }

    @Test
    @DisplayName("Un mismo autor no puede calificar dos veces al mismo vendedor")
    void rechazaSegundaResenaDelMismoAutor() {
        servicio.crear(JOSE, "maria@correo.com", new CrearResenaCommand(5, "Excelente"));

        assertThrows(ResenaDuplicadaException.class,
                () -> servicio.crear(JOSE, "maria@correo.com", new CrearResenaCommand(1, "Cambio de opinión")));
        assertEquals(1, resenas.total());
    }

    @Test
    @DisplayName("El vendedor no puede calificarse a sí mismo")
    void rechazaAutoResena() {
        assertThrows(ValorInvalidoException.class,
                () -> servicio.crear(JOSE, "JOSE@correo.com", new CrearResenaCommand(5, "Yo mismo")));
    }

    @Test
    @DisplayName("Las estrellas nulas se rechazan con el mismo mensaje de rango")
    void rechazaEstrellasNulas() {
        assertThrows(ValorInvalidoException.class,
                () -> servicio.crear(JOSE, "maria@correo.com", new CrearResenaCommand(null, "Sin estrellas")));
    }

    @Test
    @DisplayName("Un vendedor sin reseñas tiene promedio cero y lista vacía")
    void listaVaciaDevuelvePromedioCero() {
        ListadoResenas listado = servicio.listar("nadie@correo.com");

        assertEquals(0.0, listado.promedio());
        assertEquals(0, listado.total());
        assertEquals(List.of(), listado.resenas());
    }
}
