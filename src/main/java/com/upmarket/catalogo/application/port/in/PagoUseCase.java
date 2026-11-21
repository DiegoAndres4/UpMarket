package com.upmarket.catalogo.application.port.in;

import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Pago;
import java.math.BigDecimal;

public interface PagoUseCase {

    /** Cobra la tarifa de publicación al autor con la pasarela simulada. Solo el dueño puede pagar. */
    Pago pagar(Actor actor, long publicacionId);

    /** Crea una orden de PayPal (solo el dueño). Devuelve el id de la orden. */
    String crearOrdenPaypal(Actor actor, long publicacionId);

    /** Captura la orden de PayPal y, si todo coincide, aprueba el pago y activa la publicación. */
    Pago capturarPaypal(Actor actor, long publicacionId, String orderId);

    /** Configuración pública que el frontend necesita para pintar la pasarela. */
    ConfiguracionPago configuracion();

    record ConfiguracionPago(String proveedor, String clientId, BigDecimal montoUsd, String monedaPaypal) {
    }
}
