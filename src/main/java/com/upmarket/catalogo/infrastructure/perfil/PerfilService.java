package com.upmarket.catalogo.infrastructure.perfil;

import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.domain.model.TelefonoColombia;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.time.Clock;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil del usuario: ver, editar y eliminar la cuenta (en UPMarket y en AuthCore). */
@Service
public class PerfilService {

    private static final Pattern FOTO = Pattern.compile("/api/fotos/([0-9a-f-]{36}\\.(?:jpg|png))");
    private static final int MAXIMO_URL = 500;

    private final PerfilRepository perfiles;
    private final PerfilEliminadoRepository eliminados;
    private final AlmacenFotosPort almacen;
    private final AlmacenFotosLocal fotosLocal;
    private final CredencialesPort credenciales;
    private final Clock reloj;

    public PerfilService(PerfilRepository perfiles, PerfilEliminadoRepository eliminados, AlmacenFotosPort almacen,
                         AlmacenFotosLocal fotosLocal, CredencialesPort credenciales, Clock reloj) {
        this.perfiles = perfiles;
        this.eliminados = eliminados;
        this.almacen = almacen;
        this.fotosLocal = fotosLocal;
        this.credenciales = credenciales;
        this.reloj = reloj;
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
        if (foto != null && !esFotoValida(foto)) {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        Perfil p = obtener(usuario);
        if (p.getFotoUrl() != null && !p.getFotoUrl().equals(foto)) {
            borrarArchivo(p.getFotoUrl());
        }
        p.actualizar(n, tel, foto);
        return perfiles.save(p);
    }

    /** Sube la foto de perfil por el proveedor configurado y la guarda en el perfil del usuario autenticado. */
    @Transactional
    public Perfil actualizarFoto(String usuario, byte[] contenido) {
        Foto nueva = almacen.guardar(contenido, AlmacenFotosPort.CARPETA_PERFILES);
        Perfil p = obtener(usuario);
        if (p.getFotoUrl() != null && !p.getFotoUrl().equals(nueva.url())) {
            borrarArchivo(p.getFotoUrl());
        }
        p.actualizar(p.getNombre(), p.getTelefono(), nueva.url());
        return perfiles.save(p);
    }

    /** Quita la foto de perfil y borra el archivo subido (si sigue existiendo). */
    @Transactional
    public Perfil eliminarFoto(String usuario) {
        Perfil p = obtener(usuario);
        if (p.getFotoUrl() != null) {
            borrarArchivo(p.getFotoUrl());
        }
        p.actualizar(p.getNombre(), p.getTelefono(), null);
        return perfiles.save(p);
    }

    /**
     * Elimina la cuenta sin perder el registro: guarda una copia del perfil en
     * {@code perfiles_eliminados}, archiva (no borra) las publicaciones para conservar pagos y
     * recibos, borra la fila activa del perfil para liberar el usuario y, por último, elimina las
     * credenciales en AuthCore. Todo ocurre en una sola transacción: si AuthCore falla, no queda una
     * cuenta a medias.
     */
    @Transactional
    public void eliminarCuenta(Actor actor, PublicacionUseCase publicaciones, String authorizationHeader) {
        Perfil perfil = obtener(actor.username());
        eliminados.save(new PerfilEliminado(perfil.getUsuario(), perfil.getNombre(), perfil.getTelefono(),
                correoDe(perfil.getUsuario()), perfil.getFotoUrl(), reloj.instant(),
                PerfilEliminado.MOTIVO_BAJA_USUARIO));
        // Las publicaciones se archivan, nunca se borran: sus pagos y recibos deben sobrevivir.
        publicaciones.archivarDelUsuario(actor);
        perfiles.delete(perfil);
        credenciales.eliminarCuenta(authorizationHeader);
    }

    /** El nombre de usuario de AuthCore es el correo cuando la cuenta se registró con uno. */
    static String correoDe(String usuario) {
        return usuario != null && usuario.contains("@") ? usuario : null;
    }

    private static boolean esFotoValida(String foto) {
        return foto.length() <= MAXIMO_URL && (FOTO.matcher(foto).matches() || foto.startsWith("https://"));
    }

    /** Borra tanto los nombres locales heredados (/api/fotos/...) como las URLs absolutas del proveedor actual. */
    private void borrarArchivo(String url) {
        fotosLocal.borrar(url);
        almacen.borrar(url);
    }

    static String nombrePorDefecto(String usuario) {
        int arroba = usuario.indexOf('@');
        String base = arroba > 0 ? usuario.substring(0, arroba) : usuario;
        return base.length() > 60 ? base.substring(0, 60) : base;
    }
}
