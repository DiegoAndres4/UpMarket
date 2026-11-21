package com.upmarket.catalogo.application.port.out;

import java.math.BigDecimal;

/**
 * Puerto de salida hacia PayPal (Orders v2). La aplicación decide qué se aprueba; el adaptador solo
 * habla con PayPal y traduce su respuesta. Sin HTTP ni Spring en la capa de aplicación.
 */
public interface PasarelaPaypalPort {

    /**
     * Crea una orden de captura de {@code montoUsd} para la publicación. {@code invoiceId} es único
     * por intento. Devuelve el {@code id} de la orden de PayPal.
     */
    String crearOrden(long publicacionId, String invoiceId, BigDecimal montoUsd);

    /** Captura la orden de forma idempotente: PayPal recibe el mismo {@code orderId} como request id. */
    ResultadoCaptura capturar(String orderId);

    /**
     * Consulta una orden ya creada. Devuelve su estado de orden y, si ya está capturada, los datos de
     * la captura (referencia, monto, moneda y custom_id).
     */
    ResultadoOrden consultarOrden(String orderId);

    record ResultadoCaptura(String estado, String referencia, BigDecimal monto, String moneda, String customId) {
    }

    record ResultadoOrden(String estado, String estadoCaptura, String referencia, BigDecimal monto, String moneda,
                          String customId) {
    }
}
