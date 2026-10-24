package com.upmarket.catalogo.application.port.in;

/** Una categoría con cuántas publicaciones disponibles tiene. */
public record CategoriaResumen(String nombre, long disponibles) { }
