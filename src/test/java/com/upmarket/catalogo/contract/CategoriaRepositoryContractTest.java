package com.upmarket.catalogo.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.domain.model.Categoria;
import com.upmarket.catalogo.testsupport.Datos;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Toda implementación del puerto (memoria, JPA…) debe pasar estas mismas pruebas (Liskov). */
public abstract class CategoriaRepositoryContractTest {

    protected abstract CategoriaRepositoryPort repo();

    @Test
    void guardaYBuscaPorClave() {
        Categoria g = repo().guardar(Datos.categoria("Útiles académicos"));
        assertNotNull(g.getId());
        Categoria r = repo().buscarPorClave("utiles academicos").orElseThrow();
        assertEquals(g.getId(), r.getId());
        assertEquals("Útiles académicos", r.getNombre());
        assertTrue(repo().buscarPorClave("nada").isEmpty());
    }

    @Test
    void listaOrdenadaPorNombre() {
        repo().guardar(Datos.categoria("Servicios"));
        repo().guardar(Datos.categoria("Alimentos"));
        repo().guardar(Datos.categoria("Libros"));
        List<Categoria> r = repo().listar();
        assertEquals(List.of("Alimentos", "Libros", "Servicios"), r.stream().map(Categoria::getNombre).toList());
    }
}
