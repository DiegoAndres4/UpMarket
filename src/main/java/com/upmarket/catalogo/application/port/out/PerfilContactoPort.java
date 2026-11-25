package com.upmarket.catalogo.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Datos de contacto visibles para el autor de una publicación. El perfil público sigue ocultando el
 * celular; este puerto solo lo usa la lista de interesados del propio autor.
 */
public interface PerfilContactoPort {

    Map<String, ContactoPerfil> contactos(Collection<String> usuarios);
}
