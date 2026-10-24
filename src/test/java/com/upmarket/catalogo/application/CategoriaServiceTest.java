package com.upmarket.catalogo.application;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.application.port.in.CategoriaResumen;
import com.upmarket.catalogo.domain.exception.*;
import com.upmarket.catalogo.domain.model.Categoria;
import com.upmarket.catalogo.testsupport.Datos;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-9. */
class CategoriaServiceTest {

    private final Escenario e = new Escenario();

    @Test
    @DisplayName("Las categorías iniciales existen y asegurarlas dos veces no duplica")
    void iniciales() {
        assertEquals(6, e.categoriaService.listar().size());
        assertEquals(0, e.categoriaService.asegurarIniciales());
        assertEquals(6, e.categoriaService.listar().size());
    }

    @Test
    @DisplayName("El listado trae el conteo de publicaciones disponibles de cada categoría")
    void conteo() {
        e.publicar("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
        e.publicar("jose", "Portátil", "Core i5 con 8 GB de RAM", "900000", "tecnologia");
        var libro = e.publicar("jose", "Cálculo de Stewart", "Séptima edición", "80000", "Libros");
        e.publicacionService.marcarVendida(Datos.estudiante("jose"), libro.getId());
        List<CategoriaResumen> r = e.categoriaService.listar();
        assertEquals(2, r.stream().filter(c -> c.nombre().equals("Tecnología")).findFirst().orElseThrow().disponibles());
        assertEquals(0, r.stream().filter(c -> c.nombre().equals("Libros")).findFirst().orElseThrow().disponibles());
        assertEquals(0, r.stream().filter(c -> c.nombre().equals("Servicios")).findFirst().orElseThrow().disponibles());
    }

    @Test
    @DisplayName("El ADMIN crea categorías; el nombre repetido (sin importar mayúsculas ni tildes) es 409")
    void crear() {
        Categoria c = e.categoriaService.crear(e.admin(), "  Deportes ");
        assertNotNull(c.getId());
        assertEquals("Deportes", c.getNombre());
        assertEquals(7, e.categoriaService.listar().size());
        assertThrows(CategoriaDuplicadaException.class, () -> e.categoriaService.crear(e.admin(), "DEPORTES"));
        assertThrows(CategoriaDuplicadaException.class, () -> e.categoriaService.crear(e.admin(), "utiles ACADEMICOS"));
    }

    @Test
    @DisplayName("Crear categorías es solo del ADMIN; nombre vacío o corto es 400")
    void permisosYValidacion() {
        assertThrows(AccesoDenegadoException.class, () -> e.categoriaService.crear(Datos.estudiante("maria"), "Deportes"));
        assertThrows(ValorInvalidoException.class, () -> e.categoriaService.crear(e.admin(), " "));
        assertThrows(ValorInvalidoException.class, () -> e.categoriaService.crear(e.admin(), "x"));
    }
}
