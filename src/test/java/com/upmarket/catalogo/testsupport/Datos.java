package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Datos de ejemplo. El reloj fijo es martes 20 de octubre de 2026, 10:00 en Bogotá. */
public final class Datos {

    public static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private Datos() { }

    public static Clock relojFijo() { return Clock.fixed(AHORA, BOGOTA); }

    public static RelojAjustable relojAjustable() { return new RelojAjustable(AHORA, BOGOTA); }

    public static Actor admin() { return Actor.de("admin", List.of("ADMIN", "USER")); }

    public static Actor estudiante(String nombre) { return Actor.de(nombre, List.of("USER")); }

    public static Categoria categoria(String nombre) { return Categoria.crear(nombre); }

    public static Contacto contacto() { return new Contacto("3001234567", "maria@correo.com"); }

    public static Foto foto(String nombre) { return new Foto("https://archivos.example/" + nombre + ".jpg", "image/jpeg", 200_000); }

    /** Calculadora científica, 50.000, usada, categoría Tecnología, vendida por «maria». */
    public static Publicacion publicacion() {
        return publicacion("maria", "Calculadora científica", "Casio fx-991, casi nueva", "50000", "Tecnología");
    }

    public static Publicacion publicacion(String vendedor, String titulo, String descripcion, String precio, String categoria) {
        return Publicacion.publicar(titulo, descripcion, precio, EstadoArticulo.USADO, categoria(categoria),
                List.of(foto("f1")), contacto(), vendedor, AHORA);
    }
}
