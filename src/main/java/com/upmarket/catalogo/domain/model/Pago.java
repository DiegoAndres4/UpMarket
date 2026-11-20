package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago simulado por publicar un artículo. Nace PENDIENTE y solo la pasarela lo aprueba o rechaza.
 */
public class Pago {

    private final Long id;
    private final Long publicacionId;
    private final BigDecimal monto;
    private final Instant creadoEn;
    private EstadoPago estado;
    private String referencia;
    private Instant aprobadoEn;

    private Pago(Long id, Long publicacionId, BigDecimal monto, EstadoPago estado, String referencia,
                 Instant creadoEn, Instant aprobadoEn) {
        this.id = id;
        this.publicacionId = publicacionId;
        this.monto = monto;
        this.estado = estado;
        this.referencia = referencia;
        this.creadoEn = creadoEn;
        this.aprobadoEn = aprobadoEn;
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
        return new Pago(null, publicacionId, monto.setScale(2), EstadoPago.PENDIENTE, null, ahora, null);
    }

    public static Pago reconstruir(Long id, Long publicacionId, BigDecimal monto, EstadoPago estado,
                                   String referencia, Instant creadoEn, Instant aprobadoEn) {
        return new Pago(id, publicacionId, monto, estado, referencia, creadoEn, aprobadoEn);
    }

    /** Solo un pago pendiente puede aprobarse. */
    public void aprobar(String referencia, Instant ahora) {
        if (estado != EstadoPago.PENDIENTE) {
            throw new EstadoInvalidoException("El pago ya fue procesado");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new ValorInvalidoException("La referencia del pago es obligatoria");
        }
        this.estado = EstadoPago.APROBADO;
        this.referencia = referencia;
        this.aprobadoEn = ahora;
    }

    /** Solo un pago pendiente puede rechazarse. */
    public void rechazar(String referencia, Instant ahora) {
        if (estado != EstadoPago.PENDIENTE) {
            throw new EstadoInvalidoException("El pago ya fue procesado");
        }
        this.estado = EstadoPago.RECHAZADO;
        this.referencia = referencia;
        this.aprobadoEn = ahora;
    }

    public Long getId() { return id; }
    public Long getPublicacionId() { return publicacionId; }
    public BigDecimal getMonto() { return monto; }
    public EstadoPago getEstado() { return estado; }
    public String getReferencia() { return referencia; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getAprobadoEn() { return aprobadoEn; }
}
