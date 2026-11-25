package com.upmarket.catalogo.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PublicacionSpringJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GestionFotosJpaTest {
    private final FotoSubidaRepository fotos = mock(FotoSubidaRepository.class);
    private final PublicacionSpringJpaRepository publicaciones = mock(PublicacionSpringJpaRepository.class);
    private final AlmacenFotosPort almacen = mock(AlmacenFotosPort.class);
    private final GestionFotosJpa gestion = new GestionFotosJpa(fotos, publicaciones, almacen,
            mock(PlatformTransactionManager.class));
    private final String url = "https://res.cloudinary.com/demo/image/upload/v1/upmarket/publicaciones/a.png";
    private final Foto foto = new Foto(url, "image/png", 12);
    private final Actor autor = new Actor("ana", Set.of(), 1L);

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void rechazaFotosRegistradasDeOtraCuentaYUrlsNoRegistradas() {
        when(fotos.bloquear(url)).thenReturn(Optional.of(new FotoSubida(url, 2L)), Optional.empty());
        assertThatThrownBy(() -> gestion.verificar(autor, List.of(foto), List.of())).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> gestion.verificar(autor, List.of(foto), List.of())).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void noEliminaFotosCompartidasNiFotografiasAnterioresSinRegistro() {
        when(fotos.bloquear(url)).thenReturn(Optional.of(new FotoSubida(url, 1L)), Optional.empty());
        when(publicaciones.referenciasFotoParaActualizar(url)).thenReturn(List.of(123L));
        gestion.retirar(autor, List.of(foto), List.of());
        gestion.retirar(autor, List.of(foto), List.of());
        verifyNoInteractions(almacen);
    }

    @Test
    void esperaAlCommitYConservaElPendienteSiFallaElProveedor() {
        FotoSubida registro = new FotoSubida(url, 1L);
        when(fotos.bloquear(url)).thenReturn(Optional.of(registro));
        when(almacen.borrarConResultado(url)).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();
        gestion.retirar(autor, List.of(foto), List.of());
        assertThat(registro.activa()).isFalse();
        verifyNoInteractions(almacen);
        var sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
        assertThat(sincronizaciones).hasSize(1);
        sincronizaciones.forEach(TransactionSynchronization::afterCommit);
        verify(almacen).borrarConResultado(url);
        assertThat(registro.activa()).isFalse();
    }
}
