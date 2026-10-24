package com.upmarket.catalogo.contract;

import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.testsupport.CategoriaRepositoryEnMemoria;

class CategoriaRepositoryEnMemoriaContractTest extends CategoriaRepositoryContractTest {
    private final CategoriaRepositoryEnMemoria repo = new CategoriaRepositoryEnMemoria();
    @Override protected CategoriaRepositoryPort repo() { return repo; }
}
