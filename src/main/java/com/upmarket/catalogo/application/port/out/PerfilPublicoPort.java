package com.upmarket.catalogo.application.port.out;

import java.util.Collection;
import java.util.Map;

/** Nombres visibles de los perfiles, para mostrar al autor sin acoplar las reseñas a la persistencia de perfiles. */
public interface PerfilPublicoPort {

    Map<String, String> nombres(Collection<String> usuarios);
}
