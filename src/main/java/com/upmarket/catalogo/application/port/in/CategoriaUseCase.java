package com.upmarket.catalogo.application.port.in;

import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Categoria;
import java.util.List;

/** HU-9. */
public interface CategoriaUseCase {

    /** Solo ADMIN. */
    Categoria crear(Actor actor, String nombre);

    /** Todas, por nombre, con la cantidad de publicaciones disponibles. */
    List<CategoriaResumen> listar();

    /** Crea las categorías iniciales que falten. Devuelve cuántas creó. Lo invoca el arranque, no una persona. */
    int asegurarIniciales();
}
