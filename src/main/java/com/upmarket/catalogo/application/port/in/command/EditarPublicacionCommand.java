package com.upmarket.catalogo.application.port.in.command;

import java.util.List;

/**
 * Datos editables de una publicación. {@code fotos} null conserva las actuales; una lista vacía las quita.
 */
public record EditarPublicacionCommand(long id, String titulo, String descripcion, String precio, String estado,
                                       String categoria, List<FotoComando> fotos) {

    /** Compatibilidad con los clientes que editan solo los datos y no envían fotos. */
    public EditarPublicacionCommand(long id, String titulo, String descripcion, String precio, String estado,
                                    String categoria) {
        this(id, titulo, descripcion, precio, estado, categoria, null);
    }
}
