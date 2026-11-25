package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.ContactoPerfil;
import com.upmarket.catalogo.application.port.out.PerfilContactoPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Nombre y celular de perfiles, sin base de datos. */
public class PerfilContactoEnMemoria implements PerfilContactoPort {

    private final Map<String, ContactoPerfil> contactos = new HashMap<>();

    public void registrar(String usuario, String nombre, String telefono) {
        contactos.put(usuario, new ContactoPerfil(nombre, telefono));
    }

    @Override
    public Map<String, ContactoPerfil> contactos(Collection<String> usuarios) {
        Map<String, ContactoPerfil> resultado = new HashMap<>();
        for (String usuario : usuarios) {
            resultado.put(usuario, contactos.getOrDefault(usuario, new ContactoPerfil(usuario, null)));
        }
        return resultado;
    }
}
