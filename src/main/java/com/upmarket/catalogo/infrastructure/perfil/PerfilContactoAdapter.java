package com.upmarket.catalogo.infrastructure.perfil;

import com.upmarket.catalogo.application.port.out.ContactoPerfil;
import com.upmarket.catalogo.application.port.out.PerfilContactoPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Nombre y celular de los interesados, para el autor de la publicación. */
@Component
public class PerfilContactoAdapter implements PerfilContactoPort {

    private final PerfilRepository perfiles;

    public PerfilContactoAdapter(PerfilRepository perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    public Map<String, ContactoPerfil> contactos(Collection<String> usuarios) {
        Map<String, ContactoPerfil> contactos = new HashMap<>();
        perfiles.findAllById(usuarios)
                .forEach(p -> contactos.put(p.getUsuario(), new ContactoPerfil(p.getNombre(), p.getTelefono())));
        for (String usuario : usuarios) {
            contactos.putIfAbsent(usuario, new ContactoPerfil(PerfilService.nombrePorDefecto(usuario), null));
        }
        return contactos;
    }
}
