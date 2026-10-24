package com.upmarket.catalogo.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.*;
import com.upmarket.catalogo.testsupport.Datos;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class PublicacionRepositoryContractTest {

    protected abstract PublicacionRepositoryPort repo();

    private Publicacion guardar(String vendedor, String titulo, String descripcion, String precio, String categoria, long minutos) {
        return repo().guardar(Publicacion.publicar(titulo, descripcion, precio, EstadoArticulo.USADO, Datos.categoria(categoria),
                List.of(Datos.foto("a"), Datos.foto("b")), Datos.contacto(), vendedor, Datos.AHORA.plus(Duration.ofMinutes(minutos))));
    }

    private static FiltroPublicacion filtro(String texto, String categoria, String min, String max, EstadoArticulo ea,
                                            EstadoPublicacion estado, String vendedor) {
        return new FiltroPublicacion(texto, categoria, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                ea, estado, vendedor);
    }

    @Test
    void guardaYRecuperaTodaLaPublicacion() {
        Publicacion g = repo().guardar(Datos.publicacion());
        assertNotNull(g.getId());
        Publicacion r = repo().buscarPorId(g.getId()).orElseThrow();
        assertEquals("Calculadora científica", r.getTitulo());
        assertEquals("Casio fx-991, casi nueva", r.getDescripcion());
        assertEquals(new BigDecimal("50000.00"), r.getPrecio().setScale(2));
        assertEquals(EstadoArticulo.USADO, r.getEstadoArticulo());
        assertEquals("Tecnología", r.getCategoria());
        assertEquals("maria", r.getVendedor());
        assertEquals(Datos.AHORA, r.getPublicadaEn());
        assertEquals(EstadoPublicacion.DISPONIBLE, r.getEstado());
        assertNull(r.getVendidaEn());
        assertEquals("3001234567", r.getContacto().whatsapp());
        assertEquals("maria@correo.com", r.getContacto().correo());
        assertEquals(1, r.getFotos().size());
        assertEquals("image/jpeg", r.getFotos().get(0).tipo());
        assertEquals(200_000, r.getFotos().get(0).tamanoBytes());
    }

    @Test
    void noEncuentraLoQueNoExiste() {
        assertTrue(repo().buscarPorId(12345).isEmpty());
    }

    @Test
    void actualizaYConservaFotosYContacto() {
        Publicacion g = repo().guardar(Datos.publicacion());
        g.editar("Calculadora Casio", "Con funda", "45000", EstadoArticulo.NUEVO, Datos.categoria("Libros"));
        g.marcarVendida(Datos.AHORA.plus(Duration.ofDays(1)));
        repo().guardar(g);
        Publicacion r = repo().buscarPorId(g.getId()).orElseThrow();
        assertEquals("Calculadora Casio", r.getTitulo());
        assertEquals(EstadoArticulo.NUEVO, r.getEstadoArticulo());
        assertEquals("Libros", r.getCategoria());
        assertEquals(EstadoPublicacion.VENDIDA, r.getEstado());
        assertEquals(Datos.AHORA.plus(Duration.ofDays(1)), r.getVendidaEn());
        assertEquals(1, r.getFotos().size());
        assertEquals("maria@correo.com", r.getContacto().correo());
    }

    @Test
    void sinFotosYConUnSoloMedioDeContacto() {
        Publicacion g = repo().guardar(Publicacion.publicar("Título", "Descripción válida", "100", EstadoArticulo.NUEVO,
                Datos.categoria("Libros"), List.of(), new Contacto(null, "a@b.co"), "maria", Datos.AHORA));
        Publicacion r = repo().buscarPorId(g.getId()).orElseThrow();
        assertEquals(0, r.getFotos().size());
        assertNull(r.getContacto().whatsapp());
        assertEquals("a@b.co", r.getContacto().correo());
    }

    @Test
    void eliminaDefinitivamente() {
        Publicacion g = repo().guardar(Datos.publicacion());
        repo().eliminar(g.getId());
        assertTrue(repo().buscarPorId(g.getId()).isEmpty());
        assertEquals(0, repo().buscar(filtro(null, null, null, null, null, null, null)).size());
    }

    @Test
    void buscaPorTextoNormalizadoEnTituloYDescripcion() {
        guardar("maria", "Calculadora científica", "Casio fx-991", "50000", "Tecnología", 0);
        guardar("jose", "Libro de cálculo", "Séptima edición", "80000", "Libros", 1);
        assertEquals(2, repo().buscar(filtro("calcul", null, null, null, null, null, null)).size());
        assertEquals(1, repo().buscar(filtro("septima", null, null, null, null, null, null)).size());
        assertEquals(1, repo().buscar(filtro("cientifica casio", null, null, null, null, null, null)).size());
        assertEquals(0, repo().buscar(filtro("bicicleta", null, null, null, null, null, null)).size());
    }

    @Test
    void elTextoDeBusquedaNoTrataLosComodinesComoComodines() {
        guardar("maria", "Descuento 50% hoy", "Oferta única", "10000", "Otros", 0);
        guardar("jose", "Descuento 5 hoy", "Oferta normal", "10000", "Otros", 1);
        assertEquals(1, repo().buscar(filtro("50%", null, null, null, null, null, null)).size());
        assertEquals(0, repo().buscar(filtro("5_", null, null, null, null, null, null)).size());
    }

    @Test
    void filtraPorCategoriaPrecioEstadoYVendedor() {
        guardar("maria", "Calculadora", "Casio fx-991", "50000", "Tecnología", 0);
        guardar("jose", "Portátil", "Core i5", "900000", "Tecnología", 1);
        Publicacion libro = guardar("jose", "Libro", "Cálculo", "80000", "Libros", 2);
        libro.marcarVendida(Datos.AHORA.plusSeconds(10));
        repo().guardar(libro);
        assertEquals(2, repo().buscar(filtro(null, "Tecnología", null, null, null, null, null)).size());
        assertEquals(1, repo().buscar(filtro(null, "Tecnología", "100000", null, null, null, null)).size());
        assertEquals(2, repo().buscar(filtro(null, null, "50000", "80000", null, null, null)).size());      // límites inclusivos
        assertEquals(3, repo().buscar(filtro(null, null, null, null, EstadoArticulo.USADO, null, null)).size());
        assertEquals(0, repo().buscar(filtro(null, null, null, null, EstadoArticulo.NUEVO, null, null)).size());
        assertEquals(2, repo().buscar(filtro(null, null, null, null, null, EstadoPublicacion.DISPONIBLE, null)).size());
        assertEquals(1, repo().buscar(filtro(null, null, null, null, null, EstadoPublicacion.VENDIDA, null)).size());
        assertEquals(2, repo().buscar(filtro(null, null, null, null, null, null, "jose")).size());
    }

    @Test
    void ordenaDeLaMasRecienteALaMasAntigua() {
        Publicacion p1 = guardar("maria", "Primera", "Descripción uno", "100", "Libros", 0);
        Publicacion p2 = guardar("maria", "Segunda", "Descripción dos", "100", "Libros", 10);
        Publicacion p3 = guardar("maria", "Tercera", "Descripción tres", "100", "Libros", 5);
        List<Long> ids = repo().buscar(filtro(null, null, null, null, null, null, null)).stream().map(Publicacion::getId).toList();
        assertEquals(List.of(p2.getId(), p3.getId(), p1.getId()), ids);
    }

    @Test
    void cuentaLasDisponiblesPorCategoria() {
        guardar("maria", "Uno", "Descripción uno", "100", "Libros", 0);
        guardar("maria", "Dos", "Descripción dos", "100", "Libros", 1);
        guardar("maria", "Tres", "Descripción tres", "100", "Tecnología", 2);
        Publicacion vendida = guardar("maria", "Cuatro", "Descripción cuatro", "100", "Tecnología", 3);
        vendida.marcarVendida(Datos.AHORA);
        repo().guardar(vendida);
        Map<String, Long> c = repo().contarDisponiblesPorCategoria();
        assertEquals(2L, (long) c.get("Libros"));
        assertEquals(1L, (long) c.get("Tecnología"));
        assertNull(c.get("Servicios"));
    }
}
