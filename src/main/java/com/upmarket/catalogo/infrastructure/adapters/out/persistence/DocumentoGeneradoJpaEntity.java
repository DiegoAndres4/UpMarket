package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Fila de {@code documento_generado}. La clave única {@code (tipo, formato, pago_id)} impide dos
 * recibos para el mismo pago; con {@code pago_id} nulo (reportes) la base permite varias filas.
 */
@Entity
@Table(name = "documento_generado",
        uniqueConstraints = @UniqueConstraint(name = "uk_documento_recibo",
                columnNames = {"tipo", "formato", "pago_id"}))
public class DocumentoGeneradoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoDocumento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatoDocumento formato;

    @Column(length = 20)
    private String numero;

    @Column(name = "pago_id")
    private Long pagoId;

    @Column(length = 30)
    private String proveedor;

    @Column(length = 255)
    private String referencia;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "generado_en", nullable = false)
    private Instant generadoEn;

    @Column(name = "generado_por", nullable = false, length = 120)
    private String generadoPor;

    protected DocumentoGeneradoJpaEntity() { }

    public DocumentoGeneradoJpaEntity(DocumentoGenerado documento) {
        this.tipo = documento.tipo();
        this.formato = documento.formato();
        this.numero = documento.numero();
        this.pagoId = documento.pagoId();
        this.proveedor = documento.proveedor();
        this.referencia = documento.referencia();
        this.nombreArchivo = documento.nombreArchivo();
        this.tamanoBytes = documento.tamanoBytes();
        this.generadoEn = documento.generadoEn();
        this.generadoPor = documento.generadoPor();
    }

    public void aplicar(DocumentoGenerado documento) {
        this.proveedor = documento.proveedor();
        this.referencia = documento.referencia();
        this.nombreArchivo = documento.nombreArchivo();
        this.tamanoBytes = documento.tamanoBytes();
        if (documento.numero() != null) {
            this.numero = documento.numero();
        }
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public DocumentoGenerado aDominio() {
        return new DocumentoGenerado(id, tipo, formato, numero, pagoId, proveedor, referencia, nombreArchivo,
                tamanoBytes == null ? 0L : tamanoBytes, generadoEn, generadoPor);
    }
}
