package com.upmarket.catalogo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** La configuración falla al arrancar si se elige PayPal sin credenciales. */
class BeanConfigTest {

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    private final BeanConfig config = new BeanConfig();

    @Test
    @DisplayName("PAGOS_PROVEEDOR=paypal exige client id y secret")
    void paypalSinCredencialesNoArranca() {
        assertThrows(IllegalStateException.class,
                () -> config.clientePaypal(RestClient.builder(), "paypal", "", "secreto", BASE));
        assertThrows(IllegalStateException.class,
                () -> config.clientePaypal(RestClient.builder(), "paypal", "cliente", "  ", BASE));
    }

    @Test
    @DisplayName("El proveedor simulado no exige credenciales de PayPal")
    void simuladoNoExigeCredenciales() {
        assertNotNull(config.clientePaypal(RestClient.builder(), "simulado", "", "", BASE));
    }
}
