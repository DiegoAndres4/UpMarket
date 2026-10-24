package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.util.List;

/** Categoría de publicaciones. Su identidad es la «clave»: el nombre sin mayúsculas ni tildes («libros» = «Libros»). */
public class Categoria {

    /** Las que existen desde el primer arranque. */
    public static final List<String> INICIALES =
            List.of("Tecnología", "Libros", "Útiles académicos", "Alimentos", "Servicios", "Otros");

    private final Long id;
    private final String nombre;
    private final String clave;

    private Categoria(Long id, String nombre, String clave) {
        this.id = id;
        this.nombre = nombre;
        this.clave = clave;
    }

    public static Categoria crear(String nombre) {
        if (nombre == null || nombre.trim().length() < 2 || nombre.trim().length() > 40) {
            throw new ValorInvalidoException("El nombre de la categoría debe tener entre 2 y 40 caracteres");
        }
        String limpio = nombre.trim().replaceAll("\\s+", " ");
        return new Categoria(null, limpio, Texto.normalizar(limpio));
    }

    public static Categoria reconstruir(Long id, String nombre, String clave) {
        return new Categoria(id, nombre, clave);
    }

    public static String claveDe(String nombre) { return Texto.normalizar(nombre); }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getClave() { return clave; }
}
