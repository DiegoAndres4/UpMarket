package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort.ResultadoCaptura;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** El adaptador arma el payload de PayPal y traduce la captura; la red está mockeada. */
class PasarelaPaypalTest {

    private final ClientePaypal cliente = mock(ClientePaypal.class);
    private final PasarelaPaypal pasarela = new PasarelaPaypal(cliente);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("La orden usa CAPTURE, USD, el monto con dos decimales, custom_id e invoice_id")
    void payloadDeLaOrden() throws Exception {
        when(cliente.obtenerToken()).thenReturn("token");
        when(cliente.crearOrden(eq("token"), anyString()))
                .thenReturn(new ClientePaypal.Orden("ORDER-9", "CREATED"));

        String id = pasarela.crearOrden(42L, "UPM-42-abc", new BigDecimal("0.75"));

        assertEquals("ORDER-9", id);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(cliente).crearOrden(eq("token"), cuerpo.capture());
        JsonNode orden = json.readTree(cuerpo.getValue());
        assertEquals("CAPTURE", orden.get("intent").asText());
        assertEquals("USD", orden.at("/purchase_units/0/amount/currency_code").asText());
        assertEquals("0.75", orden.at("/purchase_units/0/amount/value").asText());
        assertEquals("42", orden.at("/purchase_units/0/custom_id").asText());
        assertEquals("UPM-42-abc", orden.at("/purchase_units/0/invoice_id").asText());
        assertEquals(PasarelaPaypal.DESCRIPCION, orden.at("/purchase_units/0/description").asText());
    }

    @Test
    @DisplayName("Sin id de orden, PayPal se considera fallido")
    void ordenSinIdFalla() {
        when(cliente.obtenerToken()).thenReturn("token");
        when(cliente.crearOrden(eq("token"), anyString())).thenReturn(new ClientePaypal.Orden(null, "CREATED"));
        assertThrows(IllegalStateException.class,
                () -> pasarela.crearOrden(42L, "UPM-42-abc", new BigDecimal("0.75")));
    }

    @Test
    @DisplayName("La captura reenvía el orderId como PayPal-Request-Id y traduce monto, moneda y custom_id")
    void capturaTraducida() {
        when(cliente.obtenerToken()).thenReturn("token");
        when(cliente.capturar("token", "ORDER-9", "ORDER-9")).thenReturn(new ClientePaypal.Captura(
                "COMPLETED", "COMPLETED", "CAP-1", new BigDecimal("0.75"), "USD", "42"));

        ResultadoCaptura resultado = pasarela.capturar("ORDER-9");

        assertEquals("COMPLETED", resultado.estado());
        assertEquals("CAP-1", resultado.referencia());
        assertEquals(new BigDecimal("0.75"), resultado.monto());
        assertEquals("USD", resultado.moneda());
        assertEquals("42", resultado.customId());
        verify(cliente).capturar("token", "ORDER-9", "ORDER-9");
    }

    @Test
    @DisplayName("Si la captura no trae estado propio, se usa el estado de la orden")
    void capturaUsaEstadoDeLaOrden() {
        when(cliente.obtenerToken()).thenReturn("token");
        when(cliente.capturar("token", "ORDER-9", "ORDER-9")).thenReturn(new ClientePaypal.Captura(
                "COMPLETED", null, "CAP-1", new BigDecimal("0.75"), "USD", "42"));

        assertEquals("COMPLETED", pasarela.capturar("ORDER-9").estado());
    }
}
