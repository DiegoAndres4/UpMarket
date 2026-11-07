package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.Publicacion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PublicacionRepositoryJpaAdapter implements PublicacionRepositoryPort {

    private final PublicacionSpringJpaRepository jpa;

    public PublicacionRepositoryJpaAdapter(PublicacionSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Publicacion guardar(Publicacion p) {
        PublicacionJpaEntity entidad = p.getId() == null
                ? new PublicacionJpaEntity()
                : jpa.findById(p.getId()).orElseGet(PublicacionJpaEntity::new);
        entidad.actualizarDesde(p);
        return jpa.saveAndFlush(entidad).aDominio();
    }

    @Override
    public Optional<Publicacion> buscarPorId(long id) {
        return jpa.findById(id).map(PublicacionJpaEntity::aDominio);
    }

    @Override
    public void eliminar(long id) {
        jpa.deleteById(id);
    }

    @Override
    public List<Publicacion> buscar(FiltroPublicacion f) {
        String patron = f.texto() == null ? null : "%" + escaparLike(f.texto()) + "%";
        return jpa.buscar(patron, f.categoria(), f.precioMin(), f.precioMax(), f.estadoArticulo(), f.estado(),
                f.vendedor()).stream().map(PublicacionJpaEntity::aDominio).toList();
    }

    @Override
    public Map<String, Long> contarDisponiblesPorCategoria() {
        Map<String, Long> conteo = new HashMap<>();
        for (Object[] fila : jpa.contarDisponiblesPorCategoria()) {
            conteo.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return conteo;
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
