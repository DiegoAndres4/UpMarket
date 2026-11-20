package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Pago;
import java.util.List;
import java.util.Optional;

public interface PagoRepositoryPort {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorId(long id);

    Optional<Pago> buscarPorPublicacion(long publicacionId);

    /** Todos los pagos registrados, para las cifras del panel de administración. */
    List<Pago> todos();
}
