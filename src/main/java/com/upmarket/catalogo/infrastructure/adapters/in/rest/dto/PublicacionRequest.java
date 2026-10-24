package com.upmarket.catalogo.infrastructure.adapters.in.rest.dto;

import java.util.List;

/**
 * Cuerpo de publicar. El vendedor NO viaja aquí (sale del token). El precio llega como texto para validarlo en el dominio.
 * {@code estado} es la condición del artículo: NUEVO o USADO.
 */
public record PublicacionRequest(String titulo, String descripcion, String precio, String estado, String categoria,
                                 List<FotoDto> fotos, String whatsapp, String correo) { }
