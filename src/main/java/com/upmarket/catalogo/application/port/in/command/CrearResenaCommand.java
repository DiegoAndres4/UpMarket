package com.upmarket.catalogo.application.port.in.command;

/** Datos que envía el comprador. El autor no viene aquí: sale del token. */
public record CrearResenaCommand(Integer estrellas, String comentario) {
}
