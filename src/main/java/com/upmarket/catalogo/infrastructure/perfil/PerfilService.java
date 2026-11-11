package com.upmarket.catalogo.infrastructure.perfil;

import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.domain.model.TelefonoColombia;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil del usuario: ver, editar y eliminar la cuenta (en UPMarket y en AuthCore). */
@Service
public class PerfilService {

    private static final Pattern FOTO = Pattern.compile("/api/fotos/([0-9a-f-]{36}\\.(?:jpg|png))");

    private final PerfilRepository perfiles;
    private final AlmacenFotosLocal fotos;
    private final CredencialesPort credenciales;

    public PerfilService(PerfilRepository perfiles, AlmacenFotosLocal fotos, CredencialesPort credenciales) {
        this.perfiles = perfiles;
        this.fotos = fotos;
        this.credenciales = credenciales;
    }

    @Transactional
    public Perfil obtener(String usuario) {
        return perfiles.findById(usuario).orElseGet(() -> perfiles.save(new Perfil(usuario, nombrePorDefecto(usuario))));
    }

    /** Lo que ve otro usuario: nombre y foto, nunca el celular. */
    @Transactional(readOnly = true)
    public Perfil publico(String usuario) {
        return perfiles.findById(usuario).orElseGet(() -> new Perfil(usuario, nombrePorDefecto(usuario)));
    }

    @Transactional
    public Perfil actualizar(String usuario, String nombre, String telefono, String fotoUrl) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() < 2 || n.length() > 60) {
            throw new ValorInvalidoException("El nombre debe tener entre 2 y 60 caracteres");
        }
        String tel = TelefonoColombia.limpiar(telefono);
        if (tel != null && !TelefonoColombia.esValido(tel)) {
            throw new ValorInvalidoException(TelefonoColombia.MENSAJE);
        }
        String foto = fotoUrl == null || fotoUrl.isBlank() ? null : fotoUrl.trim();
        if (foto != null && !FOTO.matcher(foto).matches()) {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        Perfil p = obtener(usuario);
        if (p.getFotoUrl() != null && !p.getFotoUrl().equals(foto)) {
            borrarArchivo(p.getFotoUrl());
        }
        p.actualizar(n, tel, foto);
        return perfiles.save(p);
    }

    /**
     * Borra las publicaciones del usuario (con sus fotos), su foto de perfil y sus datos, y después elimina sus
     * credenciales en AuthCore: el username queda libre y el login deja de funcionar. Si AuthCore falla se lanza una
     * excepción y la transacción revierte, para no dejar una cuenta a medias.
     */
    @Transactional
    public void eliminarCuenta(Actor actor, PublicacionUseCase publicaciones, String authorizationHeader) {
        for (var p : publicaciones.mias(actor)) {
            publicaciones.eliminar(actor, p.getId());
            p.getFotos().forEach(f -> borrarArchivo(f.url()));
        }
        marcarEliminada(actor.username());
        credenciales.eliminarCuenta(authorizationHeader);
    }

    @Transactional
    void marcarEliminada(String usuario) {
        Perfil p = obtener(usuario);
        if (p.getFotoUrl() != null) {
            borrarArchivo(p.getFotoUrl());
        }
        p.marcarEliminada();
        perfiles.save(p);
    }

    private void borrarArchivo(String url) {
        var m = FOTO.matcher(url == null ? "" : url);
        if (m.matches()) {
            fotos.borrar(m.group(1));
        }
    }

    private static String nombrePorDefecto(String usuario) {
        int arroba = usuario.indexOf('@');
        String base = arroba > 0 ? usuario.substring(0, arroba) : usuario;
        return base.length() > 60 ? base.substring(0, 60) : base;
    }
}
