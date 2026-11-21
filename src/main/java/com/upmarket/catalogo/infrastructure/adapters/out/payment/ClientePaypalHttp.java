package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de PayPal (Orders v2). El token OAuth2 se pide una vez y se reutiliza hasta poco antes
 * de vencer. No hace ninguna llamada al construirse: si el proveedor es simulado, este bean existe
 * pero nunca se usa.
 */
public class ClientePaypalHttp implements ClientePaypal {

    private static final Logger log = LoggerFactory.getLogger(ClientePaypalHttp.class);
    private static final long MARGEN_TOKEN_SEGUNDOS = 60;

    private final RestClient rest;
    private final String credencialesBasicas;

    private volatile String token;
    private volatile Instant tokenVenceEn = Instant.EPOCH;

    public ClientePaypalHttp(RestClient.Builder builder, String baseUrl, String clientId, String clientSecret) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = builder.baseUrl(baseUrl.trim()).requestFactory(factory).build();
        String credenciales = (clientId == null ? "" : clientId.trim()) + ":"
                + (clientSecret == null ? "" : clientSecret.trim());
        this.credencialesBasicas = "Basic "
                + Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String obtenerToken() {
        String actual = token;
        if (actual != null && Instant.now().isBefore(tokenVenceEn)) {
            return actual;
        }
        synchronized (this) {
            if (token != null && Instant.now().isBefore(tokenVenceEn)) {
                return token;
            }
            try {
                TokenRespuesta respuesta = rest.post()
                        .uri("/v1/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, credencialesBasicas)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("grant_type=client_credentials")
                        .retrieve()
                        .body(TokenRespuesta.class);
                if (respuesta == null || respuesta.accessToken() == null || respuesta.accessToken().isBlank()) {
                    throw new IllegalStateException("PayPal no entregó un token de acceso");
                }
                token = respuesta.accessToken();
                long segundos = respuesta.expiresIn() == null ? 300 : respuesta.expiresIn();
                tokenVenceEn = Instant.now().plusSeconds(Math.max(MARGEN_TOKEN_SEGUNDOS, segundos - MARGEN_TOKEN_SEGUNDOS));
                return token;
            } catch (RestClientException e) {
                log.error("PayPal no pudo autenticar la aplicación", e);
                throw new IllegalStateException("No se pudo autenticar con PayPal; intenta de nuevo", e);
            }
        }
    }

    @Override
    public Orden crearOrden(String token, String cuerpoJson) {
        try {
            OrdenRespuesta respuesta = rest.post()
                    .uri("/v2/checkout/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpoJson)
                    .retrieve()
                    .body(OrdenRespuesta.class);
            if (respuesta == null) {
                throw new IllegalStateException("PayPal no devolvió la orden");
            }
            return new Orden(respuesta.id(), respuesta.status());
        } catch (RestClientException e) {
            log.error("PayPal no pudo crear la orden", e);
            throw new IllegalStateException("No se pudo crear la orden de PayPal; intenta de nuevo", e);
        }
    }

    @Override
    public Captura capturar(String token, String orderId, String requestId) {
        try {
            CapturaRespuesta respuesta = rest.post()
                    .uri("/v2/checkout/orders/{orderId}/capture", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("PayPal-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(CapturaRespuesta.class);
            if (respuesta == null) {
                throw new IllegalStateException("PayPal no devolvió la captura");
            }
            UnidadRespuesta unidad = primera(respuesta.purchaseUnits());
            CapturaUnidad captura = unidad == null || unidad.payments() == null
                    ? null : primera(unidad.payments().captures());
            return new Captura(respuesta.status(), captura == null ? null : captura.status(),
                    captura == null ? null : captura.id(),
                    captura == null || captura.amount() == null ? null : decimal(captura.amount().value()),
                    captura == null || captura.amount() == null ? null : captura.amount().currencyCode(),
                    unidad == null ? null : unidad.customId());
        } catch (RestClientException e) {
            log.error("PayPal no pudo capturar la orden {}", orderId, e);
            throw new IllegalStateException("No se pudo capturar el pago de PayPal; intenta de nuevo", e);
        }
    }

    private static <T> T primera(List<T> lista) {
        return lista == null || lista.isEmpty() ? null : lista.get(0);
    }

    private static BigDecimal decimal(String valor) {
        try {
            return valor == null ? null : new BigDecimal(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record TokenRespuesta(@JsonProperty("access_token") String accessToken,
                                  @JsonProperty("expires_in") Long expiresIn) { }

    record OrdenRespuesta(String id, String status) { }

    record CapturaRespuesta(String id, String status,
                                    @JsonProperty("purchase_units") List<UnidadRespuesta> purchaseUnits) { }

    record UnidadRespuesta(@JsonProperty("custom_id") String customId, PagosRespuesta payments) { }

    record PagosRespuesta(List<CapturaUnidad> captures) { }

    record CapturaUnidad(String id, String status, MontoRespuesta amount) { }

    record MontoRespuesta(@JsonProperty("currency_code") String currencyCode, String value) { }
}
