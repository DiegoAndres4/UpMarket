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

    Publicacion detalle(Actor actor, long id);

    /** «Contactar al vendedor»: entrega el contacto de una publicación disponible. */
    Contacto contacto(Actor actor, long id);

    /** Personas que pidieron el contacto de una publicación. Solo el autor puede verlas. */
    List<InteresadoVista> interesados(Actor actor, long id);

    /** Todas las del usuario, vendidas incluidas, de la más reciente a la más antigua. */
    List<Publicacion> mias(Actor actor);

    Publicacion editar(Actor actor, EditarPublicacionCommand comando);

    Publicacion marcarVendida(Actor actor, long id);

    void eliminar(Actor actor, long id);

    /**
     * Marca como archivadas todas las publicaciones del usuario. Se usa al eliminar su cuenta: la
     * publicación desaparece de la vista pública y del historial del usuario, pero el registro se
     * conserva junto con sus pagos y recibos.
     */
    void archivarDelUsuario(Actor actor);
}
