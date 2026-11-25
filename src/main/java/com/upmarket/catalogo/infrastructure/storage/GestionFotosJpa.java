package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.application.port.out.GestionFotosPort;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PublicacionSpringJpaRepository;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
public class GestionFotosJpa implements GestionFotosPort {
    private static final Logger LOG = LoggerFactory.getLogger(GestionFotosJpa.class);
    private final FotoSubidaRepository fotos;
    private final PublicacionSpringJpaRepository publicaciones;
    private final AlmacenFotosPort almacen;
    private final TransactionTemplate nuevaTransaccion;

    public GestionFotosJpa(FotoSubidaRepository fotos, PublicacionSpringJpaRepository publicaciones,
                           AlmacenFotosPort almacen, PlatformTransactionManager gestor) {
        this.fotos = fotos;
        this.publicaciones = publicaciones;
        this.almacen = almacen;
        this.nuevaTransaccion = new TransactionTemplate(gestor);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void verificar(Actor actor, List<Foto> entradas, List<Foto> anteriores) {
        var existentes = anteriores.stream().map(Foto::url).collect(java.util.stream.Collectors.toSet());
        for (String url : entradas.stream().map(Foto::url).distinct().sorted().toList()) {
            var registro = fotos.bloquear(url);
            registro.ifPresent(f -> {
                if (!f.activa() || (!existentes.contains(url) && !Objects.equals(f.usuarioId(), actor.uid()))) {
                    throw new ValorInvalidoException("No puedes usar esta foto en la publicación");
                }
            });
            if (registro.isEmpty() && !existentes.contains(url)
                    && (url.startsWith("/api/fotos/") || url.toLowerCase(java.util.Locale.ROOT).contains("cloudinary.com/"))) {
                throw new ValorInvalidoException("La foto no está registrada para esta cuenta");
            }
        }
    }

    @Override
    public void retirar(Actor actor, List<Foto> anteriores, List<Foto> actuales) {
        var conservadas = actuales.stream().map(Foto::url).collect(java.util.stream.Collectors.toSet());
        for (String url : anteriores.stream().map(Foto::url).distinct().sorted().toList()) {
            if (conservadas.contains(url)) continue;
            fotos.bloquear(url).ifPresent(f -> {
                if (!f.activa() || !Objects.equals(f.usuarioId(), actor.uid())
                        || !publicaciones.referenciasFotoParaActualizar(url).isEmpty()) return;
                f.pendiente();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { borrar(url); }
                });
            });
        }
    }

    private void borrar(String url) {
        try {
            if (!almacen.borrarConResultado(url)) {
                LOG.warn("No se pudo eliminar la foto pendiente {}", url);
                return;
            }
            nuevaTransaccion.executeWithoutResult(s -> fotos.bloquear(url).ifPresent(FotoSubida::borrada));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo eliminar la foto pendiente {}", url, e);
        }
    }

    /** Reintenta los fallos y los commits interrumpidos antes de llamar al proveedor. */
    @Scheduled(fixedDelayString = "${fotos.limpieza.intervalo-ms:300000}", initialDelayString = "${fotos.limpieza.intervalo-ms:300000}")
    public void reintentarPendientes() {
        fotos.findByEstadoOrderByIdAsc("PENDIENTE").forEach(f -> borrar(f.url()));
    }
}
