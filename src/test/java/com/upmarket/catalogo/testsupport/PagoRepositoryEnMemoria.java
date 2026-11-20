package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.domain.model.Pago;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Doble en memoria del repositorio de pagos, para probar la capa de aplicación sin base de datos. */
public final class PagoRepositoryEnMemoria implements PagoRepositoryPort {

    private final Map<Long, Pago> filas = new TreeMap<>();
    private long siguiente = 1;

    @Override
    public Pago guardar(Pago pago) {
        long id = pago.getId() == null ? siguiente++ : pago.getId();
        Pago copia = copiar(id, pago);
        filas.put(id, copia);
        return copiar(id, copia);
    }

    @Override
    public Optional<Pago> buscarPorId(long id) {
        Pago pago = filas.get(id);
        return pago == null ? Optional.empty() : Optional.of(copiar(id, pago));
    }

    @Override
    public Optional<Pago> buscarPorPublicacion(long publicacionId) {
        return filas.values().stream()
                .filter(p -> p.getPublicacionId() == publicacionId)
                .max(Comparator.comparing(Pago::getCreadoEn))
                .map(p -> copiar(p.getId(), p));
    }

    @Override
    public List<Pago> todos() {
        return filas.values().stream().map(p -> copiar(p.getId(), p)).toList();
    }

    private Pago copiar(Long id, Pago p) {
        return Pago.reconstruir(id, p.getPublicacionId(), p.getMonto(), p.getEstado(),
                p.getReferencia(), p.getCreadoEn(), p.getAprobadoEn());
    }
}
