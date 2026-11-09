package com.upmarket.catalogo.infrastructure.security;

import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Una cuenta eliminada en UPMarket deja de funcionar aunque su token de AuthCore siga vigente. */
public class CuentaActivaFilter extends OncePerRequestFilter {

    private final PerfilRepository perfiles;

    public CuentaActivaFilter(PerfilRepository perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u
                && perfiles.existsByUsuarioAndEliminadaTrue(u.username())) {
            SecurityContextHolder.clearContext();
            response.setStatus(401);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":401,\"mensaje\":\"Esta cuenta fue eliminada\"}");
            return;
        }
        cadena.doFilter(request, response);
    }
}
