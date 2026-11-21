package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort;
import java.math.BigDecimal;

/** Doble de PayPal para pruebas de aplicación: no hace red y permite amañar la captura. */
public class PasarelaPaypalFalsa implements PasarelaPaypalPort {

    private BigDecimal ultimoMontoUsd;
    private Long ultimaPublicacion;
    private String ultimoInvoiceId;
    private String ultimaOrden;
    private ResultadoCaptura respuesta;

    @Override
    public String crearOrden(long publicacionId, String invoiceId, BigDecimal montoUsd) {
        this.ultimaPublicacion = publicacionId;
        this.ultimoInvoiceId = invoiceId;
        this.ultimoMontoUsd = montoUsd;
        this.ultimaOrden = "ORDER-" + publicacionId;
        return ultimaOrden;
    }

    @Override
    public ResultadoCaptura capturar(String orderId) {
        if (respuesta != null) {
            return respuesta;
        }
        long publicacionId = Long.parseLong(orderId.substring("ORDER-".length()));
        return new ResultadoCaptura("COMPLETED", "CAP-" + orderId, ultimoMontoUsd == null
                ? new BigDecimal("0.75") : ultimoMontoUsd, "USD", String.valueOf(publicacionId));
    }

    /** Fija la respuesta de la próxima captura (para probar montos, monedas o custom_id erróneos). */
    public void responder(ResultadoCaptura captura) {
        this.respuesta = captura;
    }

    public BigDecimal getUltimoMontoUsd() { return ultimoMontoUsd; }
    public Long getUltimaPublicacion() { return ultimaPublicacion; }
    public String getUltimoInvoiceId() { return ultimoInvoiceId; }
    public String getUltimaOrden() { return ultimaOrden; }
}
