package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Pago;
import java.util.Optional;

public interface PagoRepositoryPort {

    Pago guardar(Pago pago);

    Optional<Pago> buscarPorPublicacion(long publicacionId);
}
