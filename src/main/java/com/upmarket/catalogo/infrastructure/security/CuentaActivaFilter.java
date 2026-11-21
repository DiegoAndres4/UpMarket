package com.upmarket.catalogo.infrastructure.security;

import com.upmarket.catalogo.infrastructure.perfil.PerfilEliminadoRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Una cuenta eliminada en UPMarket deja de funcionar aunque su token de AuthCore siga vigente. Tras
 * la baja, la fila activa de {@code perfil} desaparece y el registro queda en
 * {@code perfiles_eliminados}: se rechazan los tokens emitidos antes de esa baja. Un token nuevo,
 * de una cuenta nueva con el mismo usuario, no se bloquea.
 */
public class CuentaActivaFilter extends OncePerRequestFilter {

    private final PerfilRepository perfiles;
    private final PerfilEliminadoRepository eliminados;

    public CuentaActivaFilter(PerfilRepository perfiles, PerfilEliminadoRepository eliminados) {
        this.perfiles = perfiles;
        this.eliminados = eliminados;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u && cuentaInvalida(u)) {
            SecurityContextHolder.clearContext();
            response.setStatus(401);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":401,\"mensaje\":\"Esta cuenta fue eliminada\"}");
            return;
        }
        cadena.doFilter(request, response);
    }

    private boolean cuentaInvalida(UsuarioAutenticado u) {
        if (perfiles.existsByUsuarioAndEliminadaTrue(u.username())) {
            return true;   // filas heredadas marcadas como eliminadas
        }
        // Sin iat no se puede fechar el token: cualquier baja previa del usuario lo invalida.
        Instant emitidoEn = u.emitidoEn() == null ? Instant.EPOCH : u.emitidoEn();
        return eliminados.existsByUsuarioAndEliminadoEnAfter(u.username(), emitidoEn);
    }
}
