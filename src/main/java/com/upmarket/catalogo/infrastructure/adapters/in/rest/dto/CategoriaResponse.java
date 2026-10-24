package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import com.upmarket.catalogo.application.port.in.CategoriaResumen;
import com.upmarket.catalogo.domain.model.Categoria;

public record CategoriaResponse(String nombre, long disponibles) {

    public static CategoriaResponse de(CategoriaResumen c) { return new CategoriaResponse(c.nombre(), c.disponibles()); }

    public static CategoriaResponse de(Categoria c) { return new CategoriaResponse(c.getNombre(), 0); }
}
