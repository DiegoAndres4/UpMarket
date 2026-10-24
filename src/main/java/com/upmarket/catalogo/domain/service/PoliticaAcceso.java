package com.upmarket.catalogo.domain.service;

import com.upmarket.catalogo.domain.exception.AccesoDenegadoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Publicacion;

/** Quién puede hacer qué. Toda la regla de permisos en un solo lugar (SRP); los controladores no deciden nada. */
public class PoliticaAcceso {

    public void asegurarAdmin(Actor actor, String operacion) {
        if (!actor.esAdmin()) {
            throw new AccesoDenegadoException("Solo el administrador puede " + operacion);
        }
    }

    public boolean esAutor(Publicacion publicacion, Actor actor) {
        return actor.username().equals(publicacion.getVendedor());
    }

    /** Editar, vender y eliminar son solo del autor. */
    public void asegurarEsAutor(Publicacion publicacion, Actor actor) {
        if (!esAutor(publicacion, actor)) {
            throw new AccesoDenegadoException("Solo el autor puede modificar esta publicación");
        }
    }
}
