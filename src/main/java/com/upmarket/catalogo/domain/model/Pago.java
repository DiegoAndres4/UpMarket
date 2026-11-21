package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago por publicar un artículo. Nace PENDIENTE y solo la pasarela lo aprueba o rechaza. La tarifa
 * se cobra en COP; cuando el proveedor es PayPal se guarda además el monto en USD y el proveedor.
 */
public class Pago {

    public static final String PROVEEDOR_PAYPAL = "PAYPAL";

    private final Long id;
    private final Long publicacionId;
    private final BigDecimal monto;
    private final Instant creadoEn;
    private EstadoPago estado;
    private String referencia;
    private Instant aprobadoEn;
    private BigDecimal montoUsd;
    private String proveedor;

    private Pago(Long id, Long publicacionId, BigDecimal monto, EstadoPago estado, String referencia,
                 Instant creadoEn, Instant aprobadoEn, BigDecimal montoUsd, String proveedor) {
        this.id = id;
        this.publicacionId = publicacionId;
        this.monto = monto;
        this.estado = estado;
        this.referencia = referencia;
        this.creadoEn = creadoEn;
        this.aprobadoEn = aprobadoEn;
        this.montoUsd = montoUsd;
        this.proveedor = proveedor;
    }

    public static Pago pendiente(Long publicacionId, BigDecimal monto, Instant ahora) {
        if (publicacionId == null) {
            throw new ValorInvalidoException("El pago debe estar asociado a una publicación");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValorInvalidoException("El monto del pago debe ser mayor que cero");
        }
        if (ahora == null) {
            throw new ValorInvalidoException("La fecha del pago es obligatoria");
        }
        return new Pago(null, publicacionId, monto.setScale(2), EstadoPago.PENDIENTE, null, ahora, null, null, null);
    }

    public static Pago reconstruir(Long id, Long publicacionId, BigDecimal monto, EstadoPago estado,
                                   String referencia, Instant creadoEn, Instant aprobadoEn) {
        return reconstruir(id, publicacionId, monto, estado, referencia, creadoEn, aprobadoEn, null, null);
    }

    public static Pago reconstruir(Long id, Long publicacionId, BigDecimal monto, EstadoPago estado,
                                   String referencia, Instant creadoEn, Instant aprobadoEn, BigDecimal montoUsd,
                                   String proveedor) {
        return new Pago(id, publicacionId, monto, estado, referencia, creadoEn, aprobadoEn, montoUsd, proveedor);
    }

    /** Solo un pago pendiente puede aprobarse. */
    public void aprobar(String referencia, Instant ahora) {
        exigirPendiente();
        exigirReferencia(referencia);
        this.estado = EstadoPago.APROBADO;
        this.referencia = referencia;
        this.aprobadoEn = ahora;
    }

    /** Aprobación de PayPal: conserva la tarifa en COP y registra el monto en USD y el proveedor. */
    public void aprobarPaypal(String referencia, BigDecimal montoUsd, Instant ahora) {
        exigirPendiente();
        exigirReferencia(referencia);
        if (montoUsd == null || montoUsd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValorInvalidoException("El monto en USD del pago debe ser mayor que cero");
        }
        this.estado = EstadoPago.APROBADO;
        this.referencia = referencia;
        this.aprobadoEn = ahora;
        this.montoUsd = montoUsd.setScale(2);
        this.proveedor = PROVEEDOR_PAYPAL;
    }

    /** Solo un pago pendiente puede rechazarse. */
    public void rechazar(String referencia, Instant ahora) {
        exigirPendiente();
        this.estado = EstadoPago.RECHAZADO;
        this.referencia = referencia;
        this.aprobadoEn = ahora;
    }

    private void exigirPendiente() {
        if (estado != EstadoPago.PENDIENTE) {
            throw new EstadoInvalidoException("El pago ya fue procesado");
        }
    }

    private static void exigirReferencia(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            throw new ValorInvalidoException("La referencia del pago es obligatoria");
        }
    }

    public Long getId() { return id; }
    public Long getPublicacionId() { return publicacionId; }
    public BigDecimal getMonto() { return monto; }
    public EstadoPago getEstado() { return estado; }
    public String getReferencia() { return referencia; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getAprobadoEn() { return aprobadoEn; }
    public BigDecimal getMontoUsd() { return montoUsd; }
    public String getProveedor() { return proveedor; }
}
