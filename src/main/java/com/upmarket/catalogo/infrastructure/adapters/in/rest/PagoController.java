package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.PagoUseCase;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración de pagos que el frontend necesita para pintar la pasarela. */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    public record ConfiguracionResponse(String proveedor, String clientId, BigDecimal montoUsd,
                                        String monedaPaypal) {
    }

    private final PagoUseCase pagos;

    public PagoController(PagoUseCase pagos) {
        this.pagos = pagos;
    }

    @GetMapping("/config")
    public ConfiguracionResponse configuracion() {
        PagoUseCase.ConfiguracionPago config = pagos.configuracion();
        return new ConfiguracionResponse(config.proveedor(), config.clientId(), config.montoUsd(),
                config.monedaPaypal());
    }
}
