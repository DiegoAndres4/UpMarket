package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP se prueba con JSON con la forma real que documenta PayPal (Orders v2). El fallo
 * original era de parseo: {@code custom_id} vive en la captura, no en la unidad de compra.
 */
class ClientePaypalHttpTest {

    private static final String BASE = "https://api-m.sandbox.paypal.com";

    private MockRestServiceServer servidor;
    private ClientePaypalHttp cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ClientePaypalHttp(builder.build(), "cliente-publico", "secreto");
    }

    @Test
    @DisplayName("La captura real trae custom_id dentro de payments.captures y se lee de ahí")
    void capturaLeeCustomIdDeLaCaptura() {
        servidor.expect(requestTo(BASE + "/v2/checkout/orders/ORDER-1/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "ORDER-1"))
                .andExpect(header("Prefer", "return=representation"))
                .andRespond(withSuccess(capturaCompletada("42"), MediaType.APPLICATION_JSON));

        ClientePaypal.Captura captura = cliente.capturar("TOKEN", "ORDER-1", "ORDER-1");

        assertEquals("COMPLETED", captura.orderStatus());
        assertEquals("COMPLETED", captura.captureStatus());
        assertEquals("CAP-1", captura.captureId());
        assertEquals(new BigDecimal("0.75"), captura.monto());
        assertEquals("USD", captura.moneda());
        assertEquals("42", captura.customId());
        servidor.verify();
    }

    @Test
    @DisplayName("Si la captura no trae custom_id se usa el de la unidad de compra")
    void capturaUsaCustomIdDeLaUnidadComoRespaldo() {
        String json = capturaCompletada(null).replace("\"reference_id\": \"default\"",
                "\"reference_id\": \"default\", \"custom_id\": \"7\"");
        servidor.expect(requestTo(BASE + "/v2/checkout/orders/ORDER-1/capture"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertEquals("7", cliente.capturar("TOKEN", "ORDER-1", "ORDER-1").customId());
    }

    @Test
    @DisplayName("ORDER_ALREADY_CAPTURED (422) se recupera consultando la orden con GET")
    void ordenYaCapturadaSeRecuperaConGet() {
        servidor.expect(requestTo(BASE + "/v2/checkout/orders/ORDER-1/capture"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":"
                                + "\"ORDER_ALREADY_CAPTURED\",\"description\":\"Order already captured.\"}]}"));
        servidor.expect(requestTo(BASE + "/v2/checkout/orders/ORDER-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(capturaCompletada("42"), MediaType.APPLICATION_JSON));

        ClientePaypal.Captura captura = cliente.capturar("TOKEN", "ORDER-1", "ORDER-1");

        assertEquals("COMPLETED", captura.captureStatus());
        assertEquals("CAP-1", captura.captureId());
        assertEquals("42", captura.customId());
        servidor.verify();
    }

    @Test
    @DisplayName("Consultar una orden aprobada sin captura devuelve el estado de la orden y sin captura")
    void consultaOrdenAprobadaSinCaptura() {
        servidor.expect(requestTo(BASE + "/v2/checkout/orders/ORDER-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "ORDER-1",
                          "status": "APPROVED",
                          "purchase_units": [
                            { "reference_id": "default",
                              "amount": { "currency_code": "USD", "value": "0.75" },
                              "custom_id": "42" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ClientePaypal.Captura captura = cliente.obtenerOrden("TOKEN", "ORDER-1");

        assertEquals("APPROVED", captura.orderStatus());
        assertNull(captura.captureStatus());
        assertNull(captura.captureId());
        assertEquals("42", captura.customId());
    }

    @Test
    @DisplayName("Crear la orden pide representación completa para recibir la captura en la respuesta")
    void crearOrdenPideRepresentacion() {
        servidor.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Prefer", "return=representation"))
                .andRespond(withSuccess("{\"id\":\"ORDER-9\",\"status\":\"CREATED\"}",
                        MediaType.APPLICATION_JSON));

        ClientePaypal.Orden orden = cliente.crearOrden("TOKEN", "{\"intent\":\"CAPTURE\"}");

        assertEquals("ORDER-9", orden.id());
        assertEquals("CREATED", orden.status());
        servidor.verify();
    }

    /** Respuesta documentada de captura: custom_id en la captura; la unidad no lo repite. */
    private static String capturaCompletada(String customId) {
        String custom = customId == null ? "" : "\"custom_id\": \"" + customId + "\",";
        return """
                {
                  "id": "ORDER-1",
                  "status": "COMPLETED",
                  "purchase_units": [
                    {
                      "reference_id": "default",
                      "payments": {
                        "captures": [
                          {
                            "id": "CAP-1",
                            "status": "COMPLETED",
                            %s
                            "invoice_id": "UPM-42-abc",
                            "amount": { "currency_code": "USD", "value": "0.75" },
                            "final_capture": true
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(custom);
    }
}
