package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.Foto;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class FotoEmbeddable {

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    protected FotoEmbeddable() {
    }

    public FotoEmbeddable(String url, String tipo, long tamanoBytes) {
        this.url = url;
        this.tipo = tipo;
        this.tamanoBytes = tamanoBytes;
    }

    public static FotoEmbeddable de(Foto f) {
        return new FotoEmbeddable(f.url(), f.tipo(), f.tamanoBytes());
    }

    public Foto aDominio() {
        return new Foto(url, tipo, tamanoBytes);
    }
}
