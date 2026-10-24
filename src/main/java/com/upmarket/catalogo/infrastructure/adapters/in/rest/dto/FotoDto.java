package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.upmarket.catalogo.domain.model.Foto;

/** Una foto ya subida al almacenamiento de archivos: dónde está y sus datos. */
public record FotoDto(String url, String tipo, long tamanoBytes) {

    public static FotoDto de(Foto f) { return new FotoDto(f.url(), f.tipo(), f.tamanoBytes()); }
}
