package com.upmarket.catalogo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort.ResultadoCaptura;
import com.upmarket.catalogo.domain.exception.AccesoDenegadoException;
import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.testsupport.Datos;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PayPal en la capa de aplicación: orden, captura, validaciones y cambio de proveedor. */
class PagoServiceTest {

    private final Escenario e = new Escenario();

    private Publicacion pendiente() {
        return e.publicarPendiente("maria", "Calculadora", "Casio fx-991 casi nueva", "50000", "Tecnología");
    }

    @Test
    @DisplayName("Con proveedor simulado el pago sigue igual y no hay endpoints de PayPal")
    void simuladoSigueIgualYPaypalNoEstaDisponible() {
        Publicacion p = pendiente();
        assertThrows(EstadoInvalidoException.class,
                () -> e.pagoService.crearOrdenPaypal(Datos.estudiante("maria"), p.getId()));

        Pago pago = e.pagoService.pagar(Datos.estudiante("maria"), p.getId());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertTrue(pago.getReferencia().startsWith("SIM-"));
        assertTrue(e.publicaciones.buscarPorId(p.getId()).orElseThrow().estaPagada());
    }

    @Test
    @DisplayName("Con proveedor PayPal el pago simulado se rechaza")
    void paypalNoPermiteCobroSimulado() {
        Publicacion p = pendiente();
        assertThrows(EstadoInvalidoException.class,
                () -> e.pagoServicePaypal.pagar(Datos.estudiante("maria"), p.getId()));
    }

    @Test
    @DisplayName("Crear la orden de PayPal: solo el autor, con el monto USD configurado y un invoice único")
    void crearOrdenPaypal() {
        Publicacion p = pendiente();
        String orden = e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());

        assertEquals("ORDER-" + p.getId(), orden);
        assertEquals(p.getId(), e.paypal.getUltimaPublicacion());
        assertEquals(new BigDecimal("0.75"), e.paypal.getUltimoMontoUsd());
        assertTrue(e.paypal.getUltimoInvoiceId().startsWith("UPM-" + p.getId() + "-"));

        assertThrows(AccesoDenegadoException.class,
                () -> e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("jose"), p.getId()));
    }

    @Test
    @DisplayName("Captura completa: aprueba el pago con monto COP y USD, proveedor PAYPAL y activa la publicación")
    void capturaAprobada() {
        Publicacion p = pendiente();
        String orden = e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());

        Pago pago = e.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), orden);

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(new BigDecimal("3000.00"), pago.getMonto());
        assertEquals(new BigDecimal("0.75"), pago.getMontoUsd());
        assertEquals(Pago.PROVEEDOR_PAYPAL, pago.getProveedor());
        assertEquals("CAP-" + orden, pago.getReferencia());
        assertTrue(e.publicaciones.buscarPorId(p.getId()).orElseThrow().estaPagada());
        assertEquals(1, e.publicacionService.buscar(com.upmarket.catalogo.application.port.in.command.BuscarQuery.sinFiltros()).size());
    }

    @Test
    @DisplayName("Captura con monto, moneda o custom_id distintos: 409 y nada aprobado")
    void capturaConDatosQueNoCoincidenNoAprueba() {
        for (ResultadoCaptura mala : new ResultadoCaptura[]{
                new ResultadoCaptura("COMPLETED", "CAP", new BigDecimal("1.00"), "USD", "1"),
                new ResultadoCaptura("COMPLETED", "CAP", new BigDecimal("0.75"), "COP", "1"),
                new ResultadoCaptura("COMPLETED", "CAP", new BigDecimal("0.75"), "USD", "999")}) {
            Escenario escenario = new Escenario();
            Publicacion p = escenario.publicarPendiente("maria", "Calculadora", "Casio fx-991", "50000", "Tecnología");
            escenario.paypal.responder(new ResultadoCaptura(mala.estado(), mala.referencia(), mala.monto(),
                    mala.moneda(), mala.customId().equals("1") ? String.valueOf(p.getId()) : mala.customId()));
            String orden = escenario.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());

            assertThrows(EstadoInvalidoException.class,
                    () -> escenario.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), orden));
            assertTrue(escenario.pagos.buscarPorPublicacion(p.getId()).isEmpty());
            assertFalse(escenario.publicaciones.buscarPorId(p.getId()).orElseThrow().estaPagada());
        }
    }

    @Test
    @DisplayName("Captura no completada: 409 y nada aprobado")
    void capturaNoCompletadaNoAprueba() {
        Publicacion p = pendiente();
        e.paypal.responder(new ResultadoCaptura("PENDING", "CAP", new BigDecimal("0.75"), "USD",
                String.valueOf(p.getId())));
        String orden = e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());

        assertThrows(EstadoInvalidoException.class,
                () -> e.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), orden));
        assertTrue(e.pagos.buscarPorPublicacion(p.getId()).isEmpty());
    }

    @Test
    @DisplayName("Capturar sin orderId o con otra persona: 400 y 403")
    void capturarValidacionesDeEntrada() {
        Publicacion p = pendiente();
        String orden = e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());
        assertThrows(ValorInvalidoException.class,
                () -> e.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), " "));
        assertThrows(AccesoDenegadoException.class,
                () -> e.pagoServicePaypal.capturarPaypal(Datos.estudiante("jose"), p.getId(), orden));
    }

    @Test
    @DisplayName("No se puede crear una segunda orden ni capturar dos veces la misma publicación")
    void noSePuedePagarDosVecesConPaypal() {
        Publicacion p = pendiente();
        String orden = e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId());
        e.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), orden);

        assertThrows(EstadoInvalidoException.class,
                () -> e.pagoServicePaypal.crearOrdenPaypal(Datos.estudiante("maria"), p.getId()));
        assertThrows(EstadoInvalidoException.class,
                () -> e.pagoServicePaypal.capturarPaypal(Datos.estudiante("maria"), p.getId(), orden));
        assertEquals(1, e.pagos.todos().size());
    }
}
