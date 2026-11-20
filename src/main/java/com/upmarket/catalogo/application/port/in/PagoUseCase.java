package com.upmarket.catalogo.application.port.in;

import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Pago;

public interface PagoUseCase {

    /** Cobra la tarifa de publicación al autor. Solo el dueño puede pagar. */
    Pago pagar(Actor actor, long publicacionId);
}
