package com.upmarket.catalogo.application.port.in;

import java.time.Instant;

/** Persona que pidió el contacto, con el nombre y el celular ya resueltos desde su perfil. */
public record InteresadoVista(String nombre, String telefono, int veces, Instant ultimaVez) {
}
