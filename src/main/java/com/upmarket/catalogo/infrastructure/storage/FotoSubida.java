package com.upmarket.catalogo.infrastructure.storage;

import jakarta.persistence.*;

/** Registro de propiedad de una foto subida por un usuario de AuthCore. */
@Entity
@Table(name = "foto_subida", uniqueConstraints = @UniqueConstraint(name = "uk_foto_subida_url", columnNames = "url"))
public class FotoSubida {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 500)
    private String url;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(nullable = false, length = 20)
    private String estado;

    protected FotoSubida() { }

    public FotoSubida(String url, Long usuarioId) {
        this.url = url;
        this.usuarioId = usuarioId;
        this.estado = "ACTIVA";
    }

    public Long usuarioId() { return usuarioId; }
    public String url() { return url; }
    public boolean activa() { return "ACTIVA".equals(estado); }
    public void pendiente() { estado = "PENDIENTE"; }
    public void borrada() { estado = "BORRADA"; }
}
