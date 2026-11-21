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

    /**
     * Verifica la última orden de PayPal guardada de la publicación (solo el dueño). Si PayPal ya
     * cobró, aprueba el pago; si la orden quedó aprobada sin capturar, la captura ahora. Es
     * idempotente: una publicación ya pagada devuelve su pago sin aprobar un segundo cobro.
     */
    Pago verificarPaypal(Actor actor, long publicacionId);

    /** Configuración pública que el frontend necesita para pintar la pasarela. */
    ConfiguracionPago configuracion();

    record ConfiguracionPago(String proveedor, String clientId, BigDecimal montoUsd, String monedaPaypal,
                             boolean sandbox) {
    }
}
