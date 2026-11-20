package com.upmarket.catalogo.infrastructure.perfil;

import com.upmarket.catalogo.application.port.out.PerfilAdmin;
import com.upmarket.catalogo.application.port.out.PerfilAdminPort;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Lectura de perfiles para el administrador. No modifica ni bloquea cuentas: eso vive en AuthCore. */
@Repository
public class PerfilAdminAdapter implements PerfilAdminPort {

    private final PerfilRepository perfiles;

    public PerfilAdminAdapter(PerfilRepository perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    public List<PerfilAdmin> listar() {
        return perfiles.findAll(Sort.by(Sort.Direction.ASC, "usuario")).stream()
                .map(perfil -> new PerfilAdmin(perfil.getUsuario(), perfil.getNombre(), perfil.getUsuario(),
                        perfil.getFotoUrl(), perfil.isEliminada()))
                .toList();
    }

    @Override
    public Optional<PerfilAdmin> buscar(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            return Optional.empty();
        }
        return perfiles.findById(usuario).map(perfil -> new PerfilAdmin(perfil.getUsuario(), perfil.getNombre(),
                perfil.getUsuario(), perfil.getFotoUrl(), perfil.isEliminada()));
    }

    @Override
    public long contar() {
        return perfiles.count();
    }
}
