package com.upmarket.catalogo.application.port.out;

import java.math.BigDecimal;

/** Puerto de salida hacia una pasarela de pago real. La simulación está en infraestructura. */
public interface PasarelaPagoPort {

    ResultadoPago cobrar(BigDecimal monto, String referenciaPublicacion);

    record ResultadoPago(boolean aprobado, String referencia) {
    }
}
