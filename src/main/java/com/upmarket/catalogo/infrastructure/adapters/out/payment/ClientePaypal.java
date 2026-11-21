package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import java.math.BigDecimal;

/**
 * Cliente HTTP mínimo de PayPal, detrás de una interfaz para que las pruebas no hagan red. El
 * adaptador {@link PasarelaPaypal} arma el JSON y valida la respuesta; esta capa solo transporta.
 */
public interface ClientePaypal {

    /** Token OAuth2 de client-credentials, cacheado hasta su vencimiento. */
    String obtenerToken();

    Orden crearOrden(String token, String cuerpoJson);

    Captura capturar(String token, String orderId, String requestId);

    /** Consulta una orden ya creada (Orders v2 GET), sin capturarla. */
    Captura obtenerOrden(String token, String orderId);

    record Orden(String id, String status) { }

    record Captura(String orderStatus, String captureStatus, String captureId, BigDecimal monto, String moneda,
                   String customId) { }
}
