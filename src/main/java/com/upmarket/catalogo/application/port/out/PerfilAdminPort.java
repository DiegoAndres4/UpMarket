package com.upmarket.catalogo.application.port.out;

import java.util.List;

/** Lectura de perfiles para el panel de administración. Es de solo lectura: la cuenta vive en AuthCore. */
public interface PerfilAdminPort {

    List<PerfilAdmin> listar();

    long contar();
}
