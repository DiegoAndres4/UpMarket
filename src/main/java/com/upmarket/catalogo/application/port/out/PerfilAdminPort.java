package com.upmarket.catalogo.application.port.out;

import java.util.List;
import java.util.Optional;

/** Lectura de perfiles para el panel de administración. Es de solo lectura: la cuenta vive en AuthCore. */
public interface PerfilAdminPort {

    List<PerfilAdmin> listar();

    /** Perfil del usuario (el correo es su nombre de usuario), si existe. */
    Optional<PerfilAdmin> buscar(String usuario);

    /**
     * Última copia archivada del perfil (cuenta eliminada) para ese usuario, si existe. Permite
     * mostrar el comprador de un pago histórico cuando la fila activa del perfil ya no está.
     */
    Optional<PerfilAdmin> buscarArchivado(String usuario);

    long contar();
}
