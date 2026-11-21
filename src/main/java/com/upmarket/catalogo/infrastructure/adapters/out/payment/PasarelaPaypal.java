package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Adaptador de PayPal: arma el payload de Orders v2 y traduce la captura. Toda la red vive en
 * {@link ClientePaypal}, que las pruebas reemplazan por un doble.
 */
public class PasarelaPaypal implements PasarelaPaypalPort {

    static final String DESCRIPCION = "Tarifa de publicación UPMarket";

    private final ClientePaypal cliente;
    private final ObjectMapper json = new ObjectMapper();

    public PasarelaPaypal(ClientePaypal cliente) {
        this.cliente = cliente;
    }

    @Override
    public String crearOrden(long publicacionId, String invoiceId, BigDecimal montoUsd) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("intent", "CAPTURE");
        ArrayNode unidades = raiz.putArray("purchase_units");
        ObjectNode unidad = unidades.addObject();
        ObjectNode monto = unidad.putObject("amount");
        monto.put("currency_code", "USD");
        monto.put("value", montoUsd.setScale(2, RoundingMode.HALF_UP).toPlainString());
        unidad.put("custom_id", String.valueOf(publicacionId));
        unidad.put("invoice_id", invoiceId);
        unidad.put("description", DESCRIPCION);

        ClientePaypal.Orden orden = cliente.crearOrden(cliente.obtenerToken(), raiz.toString());
        if (orden == null || orden.id() == null || orden.id().isBlank()) {
            throw new IllegalStateException("PayPal no devolvió el id de la orden");
        }
        return orden.id();
    }

    @Override
    public ResultadoCaptura capturar(String orderId) {
        ClientePaypal.Captura captura = cliente.capturar(cliente.obtenerToken(), orderId, orderId);
        if (captura == null) {
            return new ResultadoCaptura(null, null, null, null, null);
        }
        String estado = captura.captureStatus() != null ? captura.captureStatus() : captura.orderStatus();
        return new ResultadoCaptura(estado, captura.captureId(), captura.monto(), captura.moneda(),
                captura.customId());
    }
}
