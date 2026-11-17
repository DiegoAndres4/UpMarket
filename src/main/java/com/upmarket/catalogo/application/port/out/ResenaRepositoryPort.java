package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Resena;
import java.util.List;

/** Persistencia de reseñas. Las reglas de negocio viven en el dominio y en el servicio, no aquí. */
public interface ResenaRepositoryPort {

    Resena guardar(Resena resena);

    boolean existe(String vendedor, String autor);

    List<Resena> porVendedor(String vendedor);
}
