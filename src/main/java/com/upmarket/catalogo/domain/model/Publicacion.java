package com.upmarket.catalogo.domain.model;

import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Publicación de un artículo. Entidad con comportamiento: cambia solo mediante sus métodos y cada uno defiende su regla.
 * El vendedor es el usuario de la sesión: nunca se recibe como dato.
 */
public class Publicacion {

    private final Long id;
    private String titulo;
    private String descripcion;
    private BigDecimal precio;
    private EstadoArticulo estadoArticulo;
    private String categoria;                  // nombre de la categoría
    private final List<Foto> fotos;
    private final Contacto contacto;
    private final String vendedor;
    private final Instant publicadaEn;
    private EstadoPublicacion estado;
    private Instant vendidaEn;

    private Publicacion(Long id, String titulo, String descripcion, BigDecimal precio, EstadoArticulo estadoArticulo,
                        String categoria, List<Foto> fotos, Contacto contacto, String vendedor, Instant publicadaEn,
                        EstadoPublicacion estado, Instant vendidaEn) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.precio = precio;
        this.estadoArticulo = estadoArticulo;
        this.categoria = categoria;
        this.fotos = new ArrayList<>(fotos);
        this.contacto = contacto;
        this.vendedor = vendedor;
        this.publicadaEn = publicadaEn;
        this.estado = estado;
        this.vendidaEn = vendidaEn;
    }

    /** La publicación nace DISPONIBLE. {@code precio} llega como texto y se valida aquí. */
    public static Publicacion publicar(String titulo, String descripcion, String precio, EstadoArticulo estadoArticulo,
                                       Categoria categoria, List<Foto> fotos, Contacto contacto, String vendedor,
                                       Instant ahora) {
        validarTexto(titulo, descripcion);
        if (estadoArticulo == null || categoria == null || contacto == null) {
            throw new ValorInvalidoException("Estado, categoría y contacto son obligatorios");
        }
        if (vendedor == null || vendedor.isBlank()) {
            throw new ValorInvalidoException("No se pudo identificar al vendedor");
        }
        List<Foto> lista = fotos == null ? List.of() : fotos;
        if (lista.size() > Foto.MAXIMO_POR_PUBLICACION) {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        return new Publicacion(null, titulo.trim(), descripcion.trim(), Precio.de(precio), estadoArticulo,
                categoria.getNombre(), lista, contacto, vendedor, ahora, EstadoPublicacion.DISPONIBLE, null);
    }

    public static Publicacion reconstruir(Long id, String titulo, String descripcion, BigDecimal precio,
                                          EstadoArticulo estadoArticulo, String categoria, List<Foto> fotos,
                                          Contacto contacto, String vendedor, Instant publicadaEn,
                                          EstadoPublicacion estado, Instant vendidaEn) {
        return new Publicacion(id, titulo, descripcion, precio, estadoArticulo, categoria, fotos, contacto, vendedor,
                publicadaEn, estado, vendidaEn);
    }

    private static void validarTexto(String titulo, String descripcion) {
        if (titulo == null || titulo.trim().length() < 3 || titulo.trim().length() > 100) {
            throw new ValorInvalidoException("El título debe tener entre 3 y 100 caracteres");
        }
        if (descripcion == null || descripcion.trim().length() < 5 || descripcion.trim().length() > 2000) {
            throw new ValorInvalidoException("La descripción debe tener entre 5 y 2000 caracteres");
        }
    }

    /** Cambia los datos editables. Una publicación vendida ya no se edita. */
    public void editar(String titulo, String descripcion, String precio, EstadoArticulo estadoArticulo, Categoria categoria) {
        if (estado == EstadoPublicacion.VENDIDA) {
            throw new EstadoInvalidoException("La publicación " + id + " ya fue vendida y no se puede editar");
        }
        validarTexto(titulo, descripcion);
        if (estadoArticulo == null || categoria == null) {
            throw new ValorInvalidoException("Estado y categoría son obligatorios");
        }
        BigDecimal nuevoPrecio = Precio.de(precio);
        this.titulo = titulo.trim();
        this.descripcion = descripcion.trim();
        this.precio = nuevoPrecio;
        this.estadoArticulo = estadoArticulo;
        this.categoria = categoria.getNombre();
    }

    public void marcarVendida(Instant ahora) {
        if (estado == EstadoPublicacion.VENDIDA) {
            throw new EstadoInvalidoException("La publicación " + id + " ya estaba vendida");
        }
        estado = EstadoPublicacion.VENDIDA;
        vendidaEn = ahora;
    }

    /** Entrega el contacto solo si la publicación sigue disponible. */
    public Contacto contactoDisponible() {
        if (estado == EstadoPublicacion.VENDIDA) {
            throw new EstadoInvalidoException("La publicación " + id + " ya fue vendida: no se puede contactar al vendedor");
        }
        return contacto;
    }

    public boolean estaDisponible() { return estado == EstadoPublicacion.DISPONIBLE; }

    /** Texto ya normalizado sobre el que se busca: título y descripción, sin mayúsculas ni tildes. */
    public String textoBusqueda() { return Texto.normalizar(titulo + " " + descripcion); }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public EstadoArticulo getEstadoArticulo() { return estadoArticulo; }
    public String getCategoria() { return categoria; }
    public List<Foto> getFotos() { return Collections.unmodifiableList(fotos); }
    /** Uso interno de persistencia: no se muestra al público. Para mostrarlo use {@link #contactoDisponible()}. */
    public Contacto getContacto() { return contacto; }
    public String getVendedor() { return vendedor; }
    public Instant getPublicadaEn() { return publicadaEn; }
    public EstadoPublicacion getEstado() { return estado; }
    public Instant getVendidaEn() { return vendidaEn; }
}
