package com.upmarket.catalogo.application.port.in.command;

/** Todos opcionales y combinables. Solo busca publicaciones DISPONIBLES. */
public record BuscarQuery(String texto, String categoria, String precioMin, String precioMax, String estado) {

    public static BuscarQuery sinFiltros() {
        return new BuscarQuery(null, null, null, null, null);
    }
}
