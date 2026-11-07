package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.contract.PublicacionRepositoryContractTest;
import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.testsupport.Datos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(PublicacionRepositoryJpaAdapter.class)
class PublicacionRepositoryJpaContractTest extends PublicacionRepositoryContractTest {

    @Autowired
    private PublicacionRepositoryPort repo;

    @Autowired
    private PublicacionSpringJpaRepository jpa;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limpiar() {
        jpa.deleteAll();
    }

    @Override
    protected PublicacionRepositoryPort repo() {
        return repo;
    }

    @Test
    @DisplayName("Bloqueo optimista (@Version): si otra transacción modifica la publicación en paralelo, la segunda falla con OptimisticLockingFailureException")
    void bloqueoOptimistaEnPublicacion() {
        TransactionTemplate txNueva = new TransactionTemplate(transactionManager);
        txNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Publicacion inicial = txNueva.execute(s -> repo.guardar(Datos.publicacion()));

        assertThrows(OptimisticLockingFailureException.class, () -> txNueva.execute(s1 -> {
            Publicacion lectura1 = repo.buscarPorId(inicial.getId()).orElseThrow();
            lectura1.editar("Título editado", "Descripción editada en tx1", "45000", EstadoArticulo.USADO,
                    Datos.categoria("Tecnología"));

            // Otra transacción concurrente vende la misma publicación antes de que tx1 guarde
            txNueva.execute(s2 -> {
                Publicacion lectura2 = repo.buscarPorId(inicial.getId()).orElseThrow();
                lectura2.marcarVendida(Datos.AHORA);
                return repo.guardar(lectura2);
            });

            return repo.guardar(lectura1);
        }));
    }
}
