package com.upmarket.catalogo.infrastructure.adapters.out.payment;

import com.upmarket.catalogo.application.port.out.PasarelaPagoPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pasarela de pago simulada: no cobra dinero real y siempre aprueba.
 *
 * <p>Para integrar una pasarela real (Wompi, Mercado Pago) basta con
 * implementar {@link PasarelaPagoPort} y reemplazar este componente.</p>
 */
@Component
public class PasarelaPagoSimulada implements PasarelaPagoPort {

    @Override
    public ResultadoPago cobrar(BigDecimal monto, String referenciaPublicacion) {
        return new ResultadoPago(true, "SIM-" + UUID.randomUUID());
    }
}
