package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import com.upmarket.catalogo.domain.model.Publicacion;
import java.util.*;

/** Doble de prueba: guarda copias, así un objeto modificado no cambia lo ya guardado. */
public class PublicacionRepositoryEnMemoria implements PublicacionRepositoryPort {

    private final Map<Long, Publicacion> filas = new TreeMap<>();
    private long siguiente = 1;

    private static Publicacion copiar(long id, Publicacion p) {
        return Publicacion.reconstruir(id, p.getTitulo(), p.getDescripcion(), p.getPrecio(), p.getEstadoArticulo(),
                p.getCategoria(), p.getFotos(), p.getContacto(), p.getVendedor(), p.getPublicadaEn(), p.getEstado(),
                p.getVendidaEn());
    }

    @Override
    public Publicacion guardar(Publicacion p) {
        long id = p.getId() == null ? siguiente++ : p.getId();
        filas.put(id, copiar(id, p));
        return copiar(id, p);
    }

    @Override
    public Optional<Publicacion> buscarPorId(long id) {
        return Optional.ofNullable(filas.get(id)).map(p -> copiar(id, p));
    }

    @Override
    public void eliminar(long id) {
        filas.remove(id);
    }

    @Override
    public List<Publicacion> buscar(FiltroPublicacion f) {
        List<Publicacion> r = new ArrayList<>();
        for (Publicacion p : filas.values()) {
            if ((f.texto() == null || p.textoBusqueda().contains(f.texto()))
                    && (f.categoria() == null || f.categoria().equals(p.getCategoria()))
                    && (f.precioMin() == null || p.getPrecio().compareTo(f.precioMin()) >= 0)
                    && (f.precioMax() == null || p.getPrecio().compareTo(f.precioMax()) <= 0)
                    && (f.estadoArticulo() == null || f.estadoArticulo() == p.getEstadoArticulo())
                    && (f.estado() == null || f.estado() == p.getEstado())
                    && (f.vendedor() == null || f.vendedor().equals(p.getVendedor()))) {
                r.add(copiar(p.getId(), p));
            }
        }
        r.sort(Comparator.comparing(Publicacion::getPublicadaEn).thenComparing(Publicacion::getId).reversed());
        return r;
    }

    @Override
    public Map<String, Long> contarDisponiblesPorCategoria() {
        Map<String, Long> r = new HashMap<>();
        for (Publicacion p : filas.values()) {
            if (p.getEstado() == EstadoPublicacion.DISPONIBLE) r.merge(p.getCategoria(), 1L, Long::sum);
        }
        return r;
    }
}
