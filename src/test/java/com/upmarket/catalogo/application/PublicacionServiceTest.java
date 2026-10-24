package com.upmarket.catalogo.application;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.domain.exception.*;
import com.upmarket.catalogo.domain.model.*;
import com.upmarket.catalogo.testsupport.Datos;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-8, HU-10, HU-11 y HU-12. */
class PublicacionServiceTest {

    private final Escenario e = new Escenario();

    // ---- HU-8 ----

    @Test
    @DisplayName("Publicar guarda DISPONIBLE, con el vendedor de la sesión, la fecha y la categoría canónica")
    void publicar() {
        Publicacion p = e.publicacionService.publicar(Datos.estudiante("maria"),
                e.comando("Calculadora", "Casio fx-991 casi nueva", "50000", "usado", "tecnologia"));
        assertNotNull(p.getId());
        assertEquals(EstadoPublicacion.DISPONIBLE, p.getEstado());
        assertEquals("maria", p.getVendedor());
        assertEquals("Tecnología", p.getCategoria());
        assertEquals(Datos.AHORA, p.getPublicadaEn());
        assertEquals(new BigDecimal("50000.00"), p.getPrecio());
        assertEquals(1, p.getFotos().size());
    }

    @Test
    @DisplayName("Faltantes: el error lista TODOS los campos y no se guarda nada")
    void faltantes() {
        ValorInvalidoException ex = assertThrows(ValorInvalidoException.class, () -> e.publicacionService.publicar(
                Datos.estudiante("maria"), new PublicarCommand(null, " ", null, null, null, null, "3001234567", null)));
        for (String campo : List.of("título", "descripción", "precio", "estado", "categoría")) {
            assertTrue(ex.getMessage().contains(campo));
        }
        assertEquals(0, e.publicacionService.buscar(BuscarQuery.sinFiltros()).size());
    }

    @Test
    @DisplayName("Precio inválido: mensaje claro y nada guardado")
    void precioInvalido() {
        for (String malo : new String[]{"0", "-3", "abc"}) {
            ValorInvalidoException ex = assertThrows(ValorInvalidoException.class,
                    () -> e.publicar("maria", "Calculadora", "Casio fx-991", malo, "Libros"));
            assertEquals("El precio debe ser un número mayor que cero", ex.getMessage());
        }
        assertEquals(0, e.publicacionService.mias(Datos.estudiante("maria")).size());
    }

    @Test
    @DisplayName("Fotos no permitidas, estado desconocido y falta de contacto son 400; categoría inexistente es 404")
    void otrosErrores() {
        var gif = List.of(new FotoComando("https://x/a.gif", "image/gif", 1000));
        var seis = java.util.Collections.nCopies(6, new FotoComando("https://x/a.png", "image/png", 1000));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.publicar(Datos.estudiante("maria"),
                new PublicarCommand("Título", "Descripción válida", "100", "nuevo", "Libros", gif, "3001234567", null)));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.publicar(Datos.estudiante("maria"),
                new PublicarCommand("Título", "Descripción válida", "100", "nuevo", "Libros", seis, "3001234567", null)));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.publicar(Datos.estudiante("maria"),
                e.comando("Título", "Descripción válida", "100", "roto", "Libros")));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.publicar(Datos.estudiante("maria"),
                new PublicarCommand("Título", "Descripción válida", "100", "nuevo", "Libros", null, null, null)));
        assertThrows(CategoriaNoEncontradaException.class, () -> e.publicar("maria", "Título", "Descripción válida", "100", "Inexistente"));
        assertEquals(0, e.publicacionService.mias(Datos.estudiante("maria")).size());
    }

    // ---- HU-10 ----

    private void sembrar() {
        e.publicar("maria", "Calculadora científica", "Casio fx-991 casi nueva", "50000", "Tecnología");
        e.pasar(Duration.ofMinutes(5));
        e.publicar("jose", "Cálculo de Stewart", "Libro de cálculo, séptima edición", "80000", "Libros");
        e.pasar(Duration.ofMinutes(5));
        e.publicacionService.publicar(Datos.estudiante("ana"),
                e.comando("Portátil Lenovo", "Core i5, 8 GB, ideal para programar", "900000", "nuevo", "Tecnología"));
    }

    @Test
    @DisplayName("Sin filtros: todas las disponibles, de la más reciente a la más antigua")
    void sinFiltros() {
        sembrar();
        List<Publicacion> r = e.publicacionService.buscar(BuscarQuery.sinFiltros());
        assertEquals(3, r.size());
        assertEquals("Portátil Lenovo", r.get(0).getTitulo());
        assertEquals("Calculadora científica", r.get(2).getTitulo());
    }

    @Test
    @DisplayName("Palabra clave: título o descripción, sin importar mayúsculas ni tildes")
    void palabraClave() {
        sembrar();
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery("calculo", null, null, null, null)).size());   // «Cálculo» sin tilde
        assertEquals(2, e.publicacionService.buscar(new BuscarQuery("CALCUL", null, null, null, null)).size());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery("CALCULADORA", null, null, null, null)).size());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery("programar", null, null, null, null)).size());
        assertEquals(0, e.publicacionService.buscar(new BuscarQuery("bicicleta", null, null, null, null)).size());
    }

    @Test
    @DisplayName("Filtros combinados: categoría, rango de precio y estado")
    void filtros() {
        sembrar();
        assertEquals(2, e.publicacionService.buscar(new BuscarQuery(null, "tecnologia", null, null, null)).size());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery(null, "Tecnología", "100000", null, null)).size());
        assertEquals(2, e.publicacionService.buscar(new BuscarQuery(null, null, "50000", "80000", null)).size());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery(null, "Tecnología", null, null, "nuevo")).size());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery("portatil", "Tecnología", "800000", "1000000", "NUEVO")).size());
        assertEquals(0, e.publicacionService.buscar(new BuscarQuery("portatil", "Libros", null, null, null)).size());
        assertEquals(0, e.publicacionService.buscar(new BuscarQuery(null, "Inventada", null, null, null)).size());
    }

    @Test
    @DisplayName("Filtros inválidos: mínimo mayor que máximo, precio con letras, estado desconocido o negativo son 400")
    void filtrosInvalidos() {
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.buscar(new BuscarQuery(null, null, "500", "100", null)));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.buscar(new BuscarQuery(null, null, "abc", null, null)));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.buscar(new BuscarQuery(null, null, null, "-1", null)));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.buscar(new BuscarQuery(null, null, null, null, "roto")));
    }

    @Test
    @DisplayName("Las vendidas no aparecen en las búsquedas")
    void vendidasFueraDeLaBusqueda() {
        sembrar();
        Publicacion portatil = e.publicacionService.buscar(new BuscarQuery("portatil", null, null, null, null)).get(0);
        e.publicacionService.marcarVendida(Datos.estudiante("ana"), portatil.getId());
        assertEquals(2, e.publicacionService.buscar(BuscarQuery.sinFiltros()).size());
    }

    // ---- HU-11 ----

    @Test
    @DisplayName("Detalle sin contacto; contacto en acción aparte; inexistente 404")
    void detalleYContacto() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        assertEquals("Calculadora", e.publicacionService.detalle(p.getId()).getTitulo());
        Contacto c = e.publicacionService.contacto(p.getId());
        assertEquals("3001234567", c.whatsapp());
        assertEquals("maria@correo.com", c.correo());
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.detalle(999));
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.contacto(999));
    }

    @Test
    @DisplayName("Una publicación vendida se ve pero no se puede contactar (409)")
    void vendidaSinContacto() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        e.publicacionService.marcarVendida(Datos.estudiante("maria"), p.getId());
        assertEquals(EstadoPublicacion.VENDIDA, e.publicacionService.detalle(p.getId()).getEstado());
        assertThrows(EstadoInvalidoException.class, () -> e.publicacionService.contacto(p.getId()));
    }

    // ---- HU-12 ----

    @Test
    @DisplayName("El autor edita precio y descripción")
    void editar() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        Publicacion r = e.publicacionService.editar(Datos.estudiante("maria"),
                new EditarPublicacionCommand(p.getId(), "Calculadora", "Con funda y manual", "45000", "usado", "Útiles académicos"));
        assertEquals("Con funda y manual", r.getDescripcion());
        assertEquals(new BigDecimal("45000.00"), r.getPrecio());
        assertEquals("Útiles académicos", r.getCategoria());
        assertEquals(1, e.publicacionService.buscar(new BuscarQuery("funda", null, null, null, null)).size());
    }

    @Test
    @DisplayName("Editar: otra persona 403 (aunque sea ADMIN), inexistente 404, datos inválidos 400, vendida 409")
    void erroresDeEdicion() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        var cmd = new EditarPublicacionCommand(p.getId(), "Calculadora", "Descripción nueva", "45000", "usado", "Tecnología");
        assertThrows(AccesoDenegadoException.class, () -> e.publicacionService.editar(Datos.estudiante("jose"), cmd));
        assertThrows(AccesoDenegadoException.class, () -> e.publicacionService.editar(e.admin(), cmd));
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.editar(Datos.estudiante("maria"),
                new EditarPublicacionCommand(999, "Calculadora", "Descripción nueva", "45000", "usado", "Tecnología")));
        assertThrows(ValorInvalidoException.class, () -> e.publicacionService.editar(Datos.estudiante("maria"),
                new EditarPublicacionCommand(p.getId(), "Calculadora", "Descripción nueva", "0", "usado", "Tecnología")));
        assertEquals("Casio fx-991 casi nueva", e.publicacionService.detalle(p.getId()).getDescripcion());
        e.publicacionService.marcarVendida(Datos.estudiante("maria"), p.getId());
        assertThrows(EstadoInvalidoException.class, () -> e.publicacionService.editar(Datos.estudiante("maria"), cmd));
    }

    @Test
    @DisplayName("Marcar como vendido: el autor sí, una vez; otra persona 403")
    void vender() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        assertThrows(AccesoDenegadoException.class, () -> e.publicacionService.marcarVendida(Datos.estudiante("jose"), p.getId()));
        e.pasar(Duration.ofDays(1));
        Publicacion v = e.publicacionService.marcarVendida(Datos.estudiante("maria"), p.getId());
        assertEquals(EstadoPublicacion.VENDIDA, v.getEstado());
        assertEquals(Datos.AHORA.plus(Duration.ofDays(1)), v.getVendidaEn());
        assertThrows(EstadoInvalidoException.class, () -> e.publicacionService.marcarVendida(Datos.estudiante("maria"), p.getId()));
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.marcarVendida(Datos.estudiante("maria"), 999));
    }

    @Test
    @DisplayName("Eliminar: solo el autor; después ya no existe")
    void eliminar() {
        Publicacion p = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        assertThrows(AccesoDenegadoException.class, () -> e.publicacionService.eliminar(Datos.estudiante("jose"), p.getId()));
        assertThrows(AccesoDenegadoException.class, () -> e.publicacionService.eliminar(e.admin(), p.getId()));
        assertEquals(1, e.publicacionService.buscar(BuscarQuery.sinFiltros()).size());
        e.publicacionService.eliminar(Datos.estudiante("maria"), p.getId());
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.detalle(p.getId()));
        assertEquals(0, e.publicacionService.buscar(BuscarQuery.sinFiltros()).size());
        assertThrows(PublicacionNoEncontradaException.class, () -> e.publicacionService.eliminar(Datos.estudiante("maria"), p.getId()));
    }

    @Test
    @DisplayName("Mis publicaciones: todas las mías, vendidas incluidas, las más recientes primero")
    void mias() {
        Publicacion a = e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        e.pasar(Duration.ofMinutes(1));
        e.publicar("maria", "Cálculo de Stewart", "Séptima edición", "80000", "Libros");
        e.publicar("jose", "Portátil", "Core i5 con 8 GB", "900000", "Tecnología");
        e.publicacionService.marcarVendida(Datos.estudiante("maria"), a.getId());
        List<Publicacion> r = e.publicacionService.mias(Datos.estudiante("maria"));
        assertEquals(2, r.size());
        assertEquals("Cálculo de Stewart", r.get(0).getTitulo());
        assertEquals(EstadoPublicacion.VENDIDA, r.get(1).getEstado());
        assertEquals(1, e.publicacionService.mias(Datos.estudiante("jose")).size());
    }
}
