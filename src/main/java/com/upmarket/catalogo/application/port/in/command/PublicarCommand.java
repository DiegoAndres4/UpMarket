package com.upmarket.catalogo.application.port.in.command;

import java.util.List;

/** El vendedor NO viaja aquí: sale de la sesión (el Actor). El precio llega como texto para validarlo en el dominio. */
public record PublicarCommand(String titulo, String descripcion, String precio, String estado, String categoria,
                              List<FotoComando> fotos, String whatsapp, String correo) { }
