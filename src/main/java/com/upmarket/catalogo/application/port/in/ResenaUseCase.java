package com.upmarket.catalogo.application.port.in;

import com.upmarket.catalogo.application.port.in.command.CrearResenaCommand;
import com.upmarket.catalogo.domain.model.Actor;

/** Caso de uso de reseñas: crear una definitiva y consultar las de un vendedor. */
public interface ResenaUseCase {

    ResenaVista crear(Actor actor, String vendedor, CrearResenaCommand comando);

    ListadoResenas listar(String vendedor);
}
