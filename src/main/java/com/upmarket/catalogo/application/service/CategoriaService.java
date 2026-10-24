package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.CategoriaResumen;
import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.application.port.out.TransaccionPort;
import com.upmarket.catalogo.domain.exception.CategoriaDuplicadaException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Categoria;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.util.List;
import java.util.Map;

public class CategoriaService implements CategoriaUseCase {

    private final CategoriaRepositoryPort categorias;
    private final PublicacionRepositoryPort publicaciones;
    private final TransaccionPort transaccion;
    private final PoliticaAcceso acceso;

    public CategoriaService(CategoriaRepositoryPort categorias, PublicacionRepositoryPort publicaciones,
                            TransaccionPort transaccion, PoliticaAcceso acceso) {
        this.categorias = categorias;
        this.publicaciones = publicaciones;
        this.transaccion = transaccion;
        this.acceso = acceso;
    }

    @Override
    public Categoria crear(Actor actor, String nombre) {
        acceso.asegurarAdmin(actor, "crear categorías");
        Validacion.exigir("nombre", nombre);
        return transaccion.ejecutar(() -> {
            Categoria nueva = Categoria.crear(nombre);
            if (categorias.buscarPorClave(nueva.getClave()).isPresent()) {
                throw new CategoriaDuplicadaException(nueva.getNombre());
            }
            return categorias.guardar(nueva);
        });
    }

    @Override
    public List<CategoriaResumen> listar() {
        Map<String, Long> conteo = publicaciones.contarDisponiblesPorCategoria();
        return categorias.listar().stream()
                .map(c -> new CategoriaResumen(c.getNombre(), conteo.getOrDefault(c.getNombre(), 0L))).toList();
    }

    @Override
    public int asegurarIniciales() {
        return transaccion.ejecutar(() -> {
            int creadas = 0;
            for (String nombre : Categoria.INICIALES) {
                Categoria c = Categoria.crear(nombre);
                if (categorias.buscarPorClave(c.getClave()).isEmpty()) {
                    categorias.guardar(c);
                    creadas++;
                }
            }
            return creadas;
        });
    }
}
