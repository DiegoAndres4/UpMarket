package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.application.port.out.*;
import com.upmarket.catalogo.domain.exception.CategoriaNoEncontradaException;
import com.upmarket.catalogo.domain.exception.PublicacionNoEncontradaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.*;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/**
 * Casos de uso de publicaciones. Orden en cada operación: datos obligatorios (400) -> existencia (404)
 * -> permiso (403) -> regla de estado (409). No contiene reglas de negocio propias: delega en el dominio.
 */
public class PublicacionService implements PublicacionUseCase {

    private final PublicacionRepositoryPort publicaciones;
    private final CategoriaRepositoryPort categorias;
    private final TransaccionPort transaccion;
    private final Clock reloj;
    private final PoliticaAcceso acceso;

    public PublicacionService(PublicacionRepositoryPort publicaciones, CategoriaRepositoryPort categorias,
                              TransaccionPort transaccion, Clock reloj, PoliticaAcceso acceso) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.acceso = acceso;
    }

    @Override
    public Publicacion publicar(Actor actor, PublicarCommand c) {
        acceso.asegurarPuedePublicar(actor);
        Validacion.exigir("título", c.titulo(), "descripción", c.descripcion(), "precio", c.precio(),
                "estado", c.estado(), "categoría", c.categoria());
        EstadoArticulo estado = EstadoArticulo.de(c.estado());
        Contacto contacto = new Contacto(c.whatsapp(), c.correo());
        List<Foto> fotos = c.fotos() == null ? List.of()
                : c.fotos().stream().map(f -> new Foto(f.url(), f.tipo(), f.tamanoBytes())).toList();
        return transaccion.ejecutar(() -> {
            Categoria categoria = cargarCategoria(c.categoria());
            return publicaciones.guardar(Publicacion.publicar(c.titulo(), c.descripcion(), c.precio(), estado, categoria,
                    fotos, contacto, actor.username(), reloj.instant()));
        });
    }

    @Override
    public List<Publicacion> buscar(BuscarQuery q) {
        BigDecimal min = vacio(q.precioMin()) ? null : Precio.filtro(q.precioMin(), "precio mínimo");
        BigDecimal max = vacio(q.precioMax()) ? null : Precio.filtro(q.precioMax(), "precio máximo");
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ValorInvalidoException("El precio mínimo debe ser menor o igual al máximo");
        }
        EstadoArticulo estado = vacio(q.estado()) ? null : EstadoArticulo.de(q.estado());
        String texto = vacio(q.texto()) ? null : Texto.normalizar(q.texto());
        String categoria = vacio(q.categoria()) ? null
                : categorias.buscarPorClave(Categoria.claveDe(q.categoria())).map(Categoria::getNombre).orElse(null);
        if (!vacio(q.categoria()) && categoria == null) return List.of();          // categoría desconocida: nada que mostrar
        return publicaciones.buscar(new FiltroPublicacion(texto, categoria, min, max, estado,
                EstadoPublicacion.DISPONIBLE, null, EstadoPago.APROBADO));
    }

    @Override
    public Publicacion detalle(Actor actor, long id) {
        return transaccion.ejecutar(() -> {
            Publicacion p = cargarVisible(actor, id);
            if (!acceso.esAutor(p, actor)) {
                publicaciones.incrementarVistas(id);
                p = cargar(id);
            }
            return p;
        });
    }

    @Override
    public Contacto contacto(Actor actor, long id) {
        return cargarVisible(actor, id).contactoDisponible();
    }

    @Override
    public List<Publicacion> mias(Actor actor) {
        return publicaciones.buscar(new FiltroPublicacion(null, null, null, null, null, null, actor.username(), null));
    }

    @Override
    public Publicacion editar(Actor actor, EditarPublicacionCommand c) {
        Validacion.exigir("título", c.titulo(), "descripción", c.descripcion(), "precio", c.precio(),
                "estado", c.estado(), "categoría", c.categoria());
        EstadoArticulo estado = EstadoArticulo.de(c.estado());
        return transaccion.ejecutar(() -> {
            Publicacion p = cargarNoArchivada(c.id());
            acceso.asegurarEsAutor(p, actor);
            Categoria categoria = cargarCategoria(c.categoria());
            p.editar(c.titulo(), c.descripcion(), c.precio(), estado, categoria);
            return publicaciones.guardar(p);
        });
    }

    @Override
    public Publicacion marcarVendida(Actor actor, long id) {
        return transaccion.ejecutar(() -> {
            Publicacion p = cargarNoArchivada(id);
            acceso.asegurarEsAutor(p, actor);
            p.marcarVendida(reloj.instant());
            return publicaciones.guardar(p);
        });
    }

    @Override
    public void eliminar(Actor actor, long id) {
        transaccion.ejecutar(() -> {
            Publicacion p = cargarNoArchivada(id);
            acceso.asegurarPuedeEliminar(p, actor);
            publicaciones.eliminar(id);
            return null;
        });
    }

    @Override
    public void archivarDelUsuario(Actor actor) {
        transaccion.ejecutar(() -> {
            var ahora = reloj.instant();
            var delUsuario = publicaciones.buscar(
                    new FiltroPublicacion(null, null, null, null, null, null, actor.username(), null));
            for (Publicacion p : delUsuario) {
                p.archivar(ahora);
                publicaciones.guardar(p);
            }
            return null;
        });
    }

    private Publicacion cargar(long id) {
        return publicaciones.buscarPorId(id).orElseThrow(() -> new PublicacionNoEncontradaException(id));
    }

    /** Una publicación archivada existe para el historial, pero no para los usuarios: responde 404. */
    private Publicacion cargarNoArchivada(long id) {
        Publicacion p = cargar(id);
        if (p.estaArchivada()) {
            throw new PublicacionNoEncontradaException(id);
        }
        return p;
    }

    /** Una publicación sin pago aprobado solo existe para su autor y para el administrador. */
    private Publicacion cargarVisible(Actor actor, long id) {
        Publicacion p = cargarNoArchivada(id);
        if (!p.estaPagada() && !acceso.esAutor(p, actor) && !actor.esAdmin()) {
            throw new PublicacionNoEncontradaException(id);
        }
        return p;
    }

    private Categoria cargarCategoria(String nombre) {
        return categorias.buscarPorClave(Categoria.claveDe(nombre))
                .orElseThrow(() -> new CategoriaNoEncontradaException(nombre.trim()));
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }
}
