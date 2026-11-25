package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import java.util.List;

/**
 * Cuerpo de editar: lo que el vendedor puede cambiar. {@code fotos} null conserva las actuales;
 * una lista vacía las quita. El contacto no se edita aquí.
 */
public record EdicionRequest(String titulo, String descripcion, String precio, String estado, String categoria,
                             List<FotoDto> fotos) { }
