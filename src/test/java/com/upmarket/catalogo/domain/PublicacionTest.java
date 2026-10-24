package com.upmarket.catalogo.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.*;
import com.upmarket.catalogo.testsupport.Datos;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicacionTest {

    @Test
    @DisplayName("Publicar crea una publicación DISPONIBLE con vendedor, fecha y categoría")
    void publicar() {
        Publicacion p = Datos.publicacion();
        assertEquals(EstadoPublicacion.DISPONIBLE, p.getEstado());
        assertEquals("maria", p.getVendedor());
        assertEquals(Datos.AHORA, p.getPublicadaEn());
        assertEquals("Tecnología", p.getCategoria());
        assertEquals(new BigDecimal("50000.00"), p.getPrecio());
        assertEquals(EstadoArticulo.USADO, p.getEstadoArticulo());
        assertEquals(1, p.getFotos().size());
        assertNull(p.getId());
        assertNull(p.getVendidaEn());
    }

    @Test
    @DisplayName("Título y descripción tienen largo mínimo y máximo")
    void textos() {
        assertThrows(ValorInvalidoException.class, () -> Datos.publicacion("maria", "ab", "Descripción válida", "100", "Libros"));
        assertThrows(ValorInvalidoException.class, () -> Datos.publicacion("maria", "Título", "abcd", "100", "Libros"));
        assertThrows(ValorInvalidoException.class, () -> Datos.publicacion("maria", "x".repeat(101), "Descripción válida", "100", "Libros"));
        assertThrows(ValorInvalidoException.class, () -> Datos.publicacion("maria", "Título", "d".repeat(2001), "100", "Libros"));
    }

    @Test
    @DisplayName("El precio debe ser un número mayor que cero")
    void precio() {
        for (String malo : new String[]{"0", "-5", "abc", "12abc", " ", null, "1000000000"}) {
            ValorInvalidoException e = assertThrows(ValorInvalidoException.class,
                    () -> Datos.publicacion("maria", "Título", "Descripción válida", malo, "Libros"));
            assertEquals("El precio debe ser un número mayor que cero", e.getMessage());
        }
        assertEquals(new BigDecimal("12.50"), Precio.de(" 12.5 "));
        assertEquals(new BigDecimal("0.00"), Precio.filtro("0", "precio mínimo"));
        assertThrows(ValorInvalidoException.class, () -> Precio.filtro("-1", "precio mínimo"));
        assertThrows(ValorInvalidoException.class, () -> Precio.filtro("x", "precio mínimo"));
    }

    @Test
    @DisplayName("Fotos: máximo 5, solo JPG o PNG, hasta 5 MB")
    void fotos() {
        List<Foto> seis = new ArrayList<>();
        for (int i = 0; i < 6; i++) seis.add(Datos.foto("f" + i));
        assertThrows(ValorInvalidoException.class, () -> Publicacion.publicar("Título", "Descripción válida", "100",
                EstadoArticulo.NUEVO, Datos.categoria("Libros"), seis, Datos.contacto(), "maria", Datos.AHORA));
        assertThrows(ValorInvalidoException.class, () -> new Foto("https://x/a.gif", "image/gif", 1000));
        assertThrows(ValorInvalidoException.class, () -> new Foto("https://x/a.pdf", "application/pdf", 1000));
        assertThrows(ValorInvalidoException.class, () -> new Foto("https://x/a.png", "image/png", 5L * 1024 * 1024 + 1));
        assertThrows(ValorInvalidoException.class, () -> new Foto("https://x/a.png", "image/png", 0));
        assertThrows(ValorInvalidoException.class, () -> new Foto(" ", "image/png", 10));
        assertEquals("image/png", new Foto("https://x/a.png", " IMAGE/PNG ", 5L * 1024 * 1024).tipo());
        Publicacion sinFotos = Publicacion.publicar("Título", "Descripción válida", "100", EstadoArticulo.NUEVO,
                Datos.categoria("Libros"), null, Datos.contacto(), "maria", Datos.AHORA);
        assertEquals(0, sinFotos.getFotos().size());
    }

    @Test
    @DisplayName("Contacto: al menos uno, con formato válido")
    void contacto() {
        assertThrows(ValorInvalidoException.class, () -> new Contacto(null, " "));
        assertThrows(ValorInvalidoException.class, () -> new Contacto("123", null));
        assertThrows(ValorInvalidoException.class, () -> new Contacto(null, "no-es-correo"));
        assertEquals("+573001234567", new Contacto("+57 300-123 4567", null).whatsapp());
        assertEquals("a@b.co", new Contacto(null, " A@B.co ").correo());
    }

    @Test
    @DisplayName("Editar cambia los datos y respeta las mismas validaciones")
    void editar() {
        Publicacion p = Datos.publicacion();
        p.editar("Calculadora Casio", "Con funda", "45000", EstadoArticulo.NUEVO, Datos.categoria("Útiles académicos"));
        assertEquals("Calculadora Casio", p.getTitulo());
        assertEquals(new BigDecimal("45000.00"), p.getPrecio());
        assertEquals(EstadoArticulo.NUEVO, p.getEstadoArticulo());
        assertEquals("Útiles académicos", p.getCategoria());
        assertThrows(ValorInvalidoException.class, () -> p.editar("Calculadora", "Con funda", "0", EstadoArticulo.NUEVO, Datos.categoria("Libros")));
        assertEquals(new BigDecimal("45000.00"), p.getPrecio());
    }

    @Test
    @DisplayName("Vender: una sola vez; vendida no se edita ni se contacta")
    void vender() {
        Publicacion p = Datos.publicacion();
        assertNotNull(p.contactoDisponible());
        p.marcarVendida(Datos.AHORA.plus(Duration.ofDays(1)));
        assertEquals(EstadoPublicacion.VENDIDA, p.getEstado());
        assertEquals(Datos.AHORA.plus(Duration.ofDays(1)), p.getVendidaEn());
        assertFalse(p.estaDisponible());
        assertThrows(EstadoInvalidoException.class, () -> p.marcarVendida(Datos.AHORA));
        assertThrows(EstadoInvalidoException.class, p::contactoDisponible);
        assertThrows(EstadoInvalidoException.class, () -> p.editar("Otro título", "Otra descripción", "10", EstadoArticulo.USADO, Datos.categoria("Libros")));
    }

    @Test
    @DisplayName("El texto de búsqueda ignora mayúsculas y tildes")
    void texto() {
        assertEquals("calculadora cientifica casio fx-991, casi nueva", Datos.publicacion().textoBusqueda());
        assertEquals("util academico", Texto.normalizar("  ÚTIL   Académico "));
        assertEquals("", Texto.normalizar(null));
    }

    @Test
    @DisplayName("Categoría: nombre válido y clave sin tildes ni mayúsculas")
    void categoria() {
        Categoria c = Categoria.crear("  Útiles   académicos ");
        assertEquals("Útiles académicos", c.getNombre());
        assertEquals("utiles academicos", c.getClave());
        assertEquals(c.getClave(), Categoria.claveDe("UTILES ACADEMICOS"));
        assertThrows(ValorInvalidoException.class, () -> Categoria.crear("a"));
        assertThrows(ValorInvalidoException.class, () -> Categoria.crear(null));
        assertThrows(ValorInvalidoException.class, () -> Categoria.crear("x".repeat(41)));
        assertEquals(EstadoArticulo.NUEVO, EstadoArticulo.de(" nuevo "));
        assertThrows(ValorInvalidoException.class, () -> EstadoArticulo.de("roto"));
        assertThrows(ValorInvalidoException.class, () -> EstadoArticulo.de(null));
    }
}
