package com.upmarket.catalogo.application.port.in;

import java.time.Instant;

/** Reseña lista para mostrar: el nombre del autor ya viene resuelto desde el perfil. */
public record ResenaVista(String autor, String autorNombre, int estrellas, String comentario, Instant creadaEn) {
}
