package com.upmarket.catalogo.contract;

import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.testsupport.PublicacionRepositoryEnMemoria;

class PublicacionRepositoryEnMemoriaContractTest extends PublicacionRepositoryContractTest {
    private final PublicacionRepositoryEnMemoria repo = new PublicacionRepositoryEnMemoria();
    @Override protected PublicacionRepositoryPort repo() { return repo; }
}
