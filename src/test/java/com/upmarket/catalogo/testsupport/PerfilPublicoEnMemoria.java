package com.upmarket.catalogo.testsupport;

import com.upmarket.catalogo.application.port.out.PerfilPublicoPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Nombres visibles de los perfiles, sin base de datos. */
public class PerfilPublicoEnMemoria implements PerfilPublicoPort {

    private final Map<String, String> porUsuario;

    public PerfilPublicoEnMemoria(Map<String, String> porUsuario) {
        this.porUsuario = new HashMap<>(porUsuario);
    }

    @Override
    public Map<String, String> nombres(Collection<String> usuarios) {
        Map<String, String> nombres = new HashMap<>();
        for (String usuario : usuarios) {
            nombres.put(usuario, porUsuario.getOrDefault(usuario, usuario));
        }
        return nombres;
    }
}
