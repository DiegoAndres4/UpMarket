package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.time.Instant;

/**
 * Reseña que un comprador deja a un vendedor. Es definitiva: no se edita ni se borra desde la aplicación.
 * El autor nunca viene del cuerpo de la petición: lo pone la capa REST a partir del token.
 */
public record Resena(Long id, String vendedor, String autor, int estrellas, String comentario, Instant creadaEn) {

    public static final int MIN_ESTRELLAS = 0;
    public static final int MAX_ESTRELLAS = 5;
    public static final int COMENTARIO_MIN = 3;
    public static final int COMENTARIO_MAX = 500;
    public static final String MENSAJE_ESTRELLAS = "Las estrellas deben ser un número entero entre 0 y 5";
    public static final String MENSAJE_COMENTARIO = "El comentario debe tener entre 3 y 500 caracteres";

    public Resena {
        vendedor = vendedor == null ? "" : vendedor.trim();
        autor = autor == null ? "" : autor.trim();
        comentario = comentario == null ? "" : comentario.trim();
        if (vendedor.isEmpty() || vendedor.length() > 120) {
            throw new ValorInvalidoException("El vendedor es obligatorio");
        }
        if (autor.isEmpty() || autor.length() > 120) {
            throw new ValorInvalidoException("El autor es obligatorio");
        }
        if (vendedor.equalsIgnoreCase(autor)) {
            throw new ValorInvalidoException("No puedes calificarte a ti mismo");
        }
        if (estrellas < MIN_ESTRELLAS || estrellas > MAX_ESTRELLAS) {
            throw new ValorInvalidoException(MENSAJE_ESTRELLAS);
        }
        if (comentario.length() < COMENTARIO_MIN || comentario.length() > COMENTARIO_MAX) {
            throw new ValorInvalidoException(MENSAJE_COMENTARIO);
        }
        if (creadaEn == null) {
            throw new ValorInvalidoException("La fecha de la reseña es obligatoria");
        }
    }

    public static Resena crear(String vendedor, String autor, int estrellas, String comentario, Instant ahora) {
        return new Resena(null, vendedor, autor, estrellas, comentario, ahora);
    }
}
