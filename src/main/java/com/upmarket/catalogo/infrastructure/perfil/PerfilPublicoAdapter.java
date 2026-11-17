package com.upmarket.catalogo.infrastructure.perfil;

import com.upmarket.catalogo.application.port.out.PerfilPublicoPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resuelve el nombre visible de varios usuarios en una sola consulta. */
@Component
public class PerfilPublicoAdapter implements PerfilPublicoPort {

    private final PerfilRepository perfiles;

    public PerfilPublicoAdapter(PerfilRepository perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    public Map<String, String> nombres(Collection<String> usuarios) {
        Map<String, String> nombres = new HashMap<>();
        perfiles.findAllById(usuarios)
                .forEach(p -> nombres.put(p.getUsuario(), p.getNombre()));
        for (String usuario : usuarios) {
            nombres.putIfAbsent(usuario, PerfilService.nombrePorDefecto(usuario));
        }
        return nombres;
    }
}
