package com.upmarket.catalogo.application.port.in;

import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Contacto;
import com.upmarket.catalogo.domain.model.Publicacion;
import java.util.List;

/** HU-8, HU-10, HU-11 y HU-12. */
public interface PublicacionUseCase {

    Publicacion publicar(Actor actor, PublicarCommand comando);

    List<Publicacion> buscar(BuscarQuery consulta);

    Publicacion detalle(long id);

    /** «Contactar al vendedor»: entrega el contacto de una publicación disponible. */
    Contacto contacto(long id);

    /** Todas las del usuario, vendidas incluidas, de la más reciente a la más antigua. */
    List<Publicacion> mias(Actor actor);

    Publicacion editar(Actor actor, EditarPublicacionCommand comando);

    Publicacion marcarVendida(Actor actor, long id);

    void eliminar(Actor actor, long id);
}
