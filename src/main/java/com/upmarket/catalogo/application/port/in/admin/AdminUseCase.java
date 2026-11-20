package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.Actor;

/**
 * Casos de uso del panel de administración. Todas las operaciones exigen el rol ADMIN; la seguridad HTTP
 * también lo exige, como primera barrera.
 */
public interface AdminUseCase {

    EstadisticasAdmin estadisticas(Actor actor);

    PaginaAdmin<PublicacionAdmin> publicaciones(Actor actor, FiltroAdminPublicaciones filtro);

    void eliminarPublicacion(Actor actor, long id);

    PaginaAdmin<UsuarioAdmin> usuarios(Actor actor, int pagina, int tamano);
}
