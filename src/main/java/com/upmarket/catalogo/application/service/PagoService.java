package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.PagoUseCase;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PasarelaPagoPort;
import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.application.port.out.TransaccionPort;
import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.PublicacionNoEncontradaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * Cobro de la tarifa de publicación. Hay dos proveedores: {@code simulado} (por defecto, sin dinero
 * real) y {@code paypal} (Orders v2 en modo sandbox). En ambos casos el pago se aprueba dentro de una
 * transacción que bloquea la publicación y vuelve a comprobar que no esté pagada: dos solicitudes
 * simultáneas no pueden aprobar dos pagos.
 */
public class PagoService implements PagoUseCase {

    public static final String PROVEEDOR_SIMULADO = "simulado";
    public static final String PROVEEDOR_PAYPAL = "paypal";
    private static final String MONEDA_PAYPAL = "USD";

    private final PagoRepositoryPort pagos;
    private final PublicacionRepositoryPort publicaciones;
    private final PasarelaPagoPort pasarela;
    private final PasarelaPaypalPort paypal;
    private final TransaccionPort transaccion;
    private final Clock reloj;
    private final PoliticaAcceso acceso;
    private final BigDecimal tarifa;
    private final String proveedor;
    private final String paypalClientId;
    private final BigDecimal montoUsd;

    public PagoService(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones,
                       PasarelaPagoPort pasarela, PasarelaPaypalPort paypal, TransaccionPort transaccion,
                       Clock reloj, PoliticaAcceso acceso, long tarifaCop, String proveedor, String paypalClientId,
                       BigDecimal montoUsd) {
        if (tarifaCop <= 0) {
            throw new ValorInvalidoException("La tarifa de publicación debe ser mayor que cero");
        }
        if (montoUsd == null || montoUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValorInvalidoException("El monto de PayPal en USD debe ser mayor que cero");
        }
        this.pagos = pagos;
        this.publicaciones = publicaciones;
        this.pasarela = pasarela;
        this.paypal = paypal;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.acceso = acceso;
        this.tarifa = BigDecimal.valueOf(tarifaCop).setScale(2);
        this.proveedor = normalizarProveedor(proveedor);
        this.paypalClientId = paypalClientId;
        this.montoUsd = montoUsd.setScale(2);
    }

    @Override
    public Pago pagar(Actor actor, long publicacionId) {
        if (!esSimulado()) {
            throw new EstadoInvalidoException("El proveedor de pagos configurado es PayPal; usa el pago con PayPal");
        }
        return transaccion.ejecutar(() -> {
            Publicacion publicacion = cargarBloqueando(publicacionId);
            acceso.asegurarEsAutor(publicacion, actor);
            exigirSinPago(publicacion);
            Pago pago = Pago.pendiente(publicacionId, tarifa, reloj.instant());
            PasarelaPagoPort.ResultadoPago resultado = pasarela.cobrar(tarifa, "PUB-" + publicacionId);
            if (!resultado.aprobado()) {
                pago.rechazar(resultado.referencia(), reloj.instant());
                pagos.guardar(pago);
                throw new EstadoInvalidoException("El pago fue rechazado: " + resultado.referencia());
            }
            pago.aprobar(resultado.referencia(), reloj.instant());
            Pago guardado = pagos.guardar(pago);
            publicacion.activarPago();
            publicaciones.guardar(publicacion);
            return guardado;
        });
    }

    @Override
    public String crearOrdenPaypal(Actor actor, long publicacionId) {
        if (!esPaypal()) {
            throw new EstadoInvalidoException("El proveedor de pagos configurado es simulado; no hay PayPal");
        }
        Publicacion publicacion = publicaciones.buscarPorId(publicacionId)
                .orElseThrow(() -> new PublicacionNoEncontradaException(publicacionId));
        acceso.asegurarEsAutor(publicacion, actor);
        exigirSinPago(publicacion);
        String invoiceId = "UPM-" + publicacionId + "-" + UUID.randomUUID();
        return paypal.crearOrden(publicacionId, invoiceId, montoUsd);
    }

    @Override
    public Pago capturarPaypal(Actor actor, long publicacionId, String orderId) {
        if (!esPaypal()) {
            throw new EstadoInvalidoException("El proveedor de pagos configurado es simulado; no hay PayPal");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new ValorInvalidoException("Falta el identificador de la orden de PayPal");
        }
        return transaccion.ejecutar(() -> {
            Publicacion publicacion = cargarBloqueando(publicacionId);
            acceso.asegurarEsAutor(publicacion, actor);
            exigirSinPago(publicacion);

            PasarelaPaypalPort.ResultadoCaptura captura = paypal.capturar(orderId.trim());
            exigirCapturaValida(captura, publicacionId);

            Pago pago = Pago.pendiente(publicacionId, tarifa, reloj.instant());
            pago.aprobarPaypal(captura.referencia(), montoUsd, reloj.instant());
            Pago guardado = pagos.guardar(pago);
            publicacion.activarPago();
            publicaciones.guardar(publicacion);
            return guardado;
        });
    }

    @Override
    public ConfiguracionPago configuracion() {
        return new ConfiguracionPago(proveedor, esPaypal() ? paypalClientId : null, montoUsd, MONEDA_PAYPAL);
    }

    /** Verifica la respuesta de PayPal: estado, moneda, monto y que la orden sea de esta publicación. */
    private void exigirCapturaValida(PasarelaPaypalPort.ResultadoCaptura captura, long publicacionId) {
        if (!"COMPLETED".equalsIgnoreCase(captura.estado()) || captura.referencia() == null
                || captura.referencia().isBlank()) {
            throw new EstadoInvalidoException("PayPal no completó el pago");
        }
        if (!MONEDA_PAYPAL.equalsIgnoreCase(captura.moneda())) {
            throw new EstadoInvalidoException("La moneda del pago de PayPal no es USD");
        }
        if (captura.monto() == null || captura.monto().compareTo(montoUsd) != 0) {
            throw new EstadoInvalidoException("El monto de PayPal no coincide con la tarifa configurada");
        }
        if (!String.valueOf(publicacionId).equals(captura.customId())) {
            throw new EstadoInvalidoException("El pago de PayPal no corresponde a esta publicación");
        }
    }

    private Publicacion cargarBloqueando(long publicacionId) {
        return publicaciones.buscarPorIdBloqueando(publicacionId)
                .orElseThrow(() -> new PublicacionNoEncontradaException(publicacionId));
    }

    /**
     * Dentro de la transacción, con la publicación bloqueada: una publicación archivada, ya pagada o
     * con un pago aprobado no se cobra otra vez.
     */
    private void exigirSinPago(Publicacion publicacion) {
        long id = publicacion.getId();
        if (publicacion.estaArchivada()) {
            throw new PublicacionNoEncontradaException(id);
        }
        if (publicacion.estaPagada() || pagos.buscarPorPublicacion(id)
                .map(pago -> pago.getEstado() == EstadoPago.APROBADO)
                .orElse(false)) {
            throw new EstadoInvalidoException("La publicación " + id + " ya está pagada");
        }
    }

    private boolean esSimulado() { return PROVEEDOR_SIMULADO.equals(proveedor); }

    private boolean esPaypal() { return PROVEEDOR_PAYPAL.equals(proveedor); }

    private static String normalizarProveedor(String valor) {
        String limpio = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (limpio.isEmpty()) {
            return PROVEEDOR_SIMULADO;
        }
        if (!PROVEEDOR_SIMULADO.equals(limpio) && !PROVEEDOR_PAYPAL.equals(limpio)) {
            throw new ValorInvalidoException("Proveedor de pagos desconocido: '" + valor
                    + "'. Valores válidos: simulado, paypal.");
        }
        return limpio;
    }
}
