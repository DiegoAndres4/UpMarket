package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.contract.CategoriaRepositoryContractTest;
import com.upmarket.catalogo.domain.exception.CategoriaDuplicadaException;
import com.upmarket.catalogo.testsupport.Datos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CategoriaRepositoryJpaAdapter.class)
class CategoriaRepositoryJpaContractTest extends CategoriaRepositoryContractTest {

    @Autowired
    private CategoriaRepositoryPort repo;

    @Autowired
    private CategoriaSpringJpaRepository jpa;

    @BeforeEach
    void limpiar() {
        jpa.deleteAll();
    }

    @Override
    protected CategoriaRepositoryPort repo() {
        return repo;
    }

    @Test
    @DisplayName("La restricción UNIQUE (uk_categoria_nombre) rechaza nombres equivalentes y traduce a CategoriaDuplicadaException")
    void restriccionUnicaEnBaseDeDatos() {
        repo.guardar(Datos.categoria("Electrónica"));
        CategoriaDuplicadaException ex = assertThrows(CategoriaDuplicadaException.class,
                () -> repo.guardar(Datos.categoria("electronica")));
        assertEquals("La categoría 'electronica' ya existe", ex.getMessage());
    }
}
