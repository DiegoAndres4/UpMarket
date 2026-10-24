package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

/** Cuerpo de editar: lo que el vendedor puede cambiar. Fotos y contacto no se editan aquí. */
public record EdicionRequest(String titulo, String descripcion, String precio, String estado, String categoria) { }
