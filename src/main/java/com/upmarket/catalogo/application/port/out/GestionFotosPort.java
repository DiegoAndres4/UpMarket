package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Foto;
import java.util.List;

/** Coordina las referencias y la limpieza de fotos dentro de la transacción de publicación. */
public interface GestionFotosPort {
    void verificar(Actor actor, List<Foto> fotos, List<Foto> anteriores);
    void retirar(Actor actor, List<Foto> anteriores, List<Foto> actuales);
}
