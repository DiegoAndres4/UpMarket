package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.Pago;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

/** Fila de la tabla {@code pago}. La referencia de la pasarela es única. */
@Entity
@Table(name = "pago",
        uniqueConstraints = @UniqueConstraint(name = "uk_pago_referencia",
                columnNames = {"referencia"}))
public class PagoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publicacion_id", nullable = false)
    private Long publicacionId;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(nullable = false, length = 80)
    private String referencia;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    protected PagoJpaEntity() { }

    public PagoJpaEntity(Pago pago) {
        this.publicacionId = pago.getPublicacionId();
        this.monto = pago.getMonto();
        this.estado = pago.getEstado();
        this.referencia = pago.getReferencia();
        this.creadoEn = pago.getCreadoEn();
        this.aprobadoEn = pago.getAprobadoEn();
    }

    public Pago aDominio() {
        return Pago.reconstruir(id, publicacionId, monto, estado, referencia, creadoEn, aprobadoEn);
    }
}
