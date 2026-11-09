package com.upmarket.catalogo.infrastructure.security;

import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Seguridad de la API REST. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    JwtTokenValidator jwtTokenValidator(JwtProperties propiedades, Clock reloj) {
        return new JwtTokenValidator(propiedades.secretBase64(), reloj);
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtTokenValidator validador, PerfilRepository perfiles)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)                       // API sin sesión ni cookies
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                // Crear categorías: solo ADMIN. Es una primera barrera en la URL; el dominio (PoliticaAcceso)
                // vuelve a verificar el rol, así que un cambio de rutas no deja un hueco.
                .requestMatchers(HttpMethod.POST, "/api/categorias").hasRole("ADMIN")
                // Fotos: ver es público (la etiqueta img no envía token); subir exige estar autenticado.
                .requestMatchers(HttpMethod.GET, "/api/fotos/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/fotos").authenticated()
                // El resto lo usa cualquier usuario autenticado; quién puede editar, vender o borrar lo decide el dominio.
                .requestMatchers("/api/categorias/**", "/api/publicaciones/**", "/api/perfil", "/api/perfil/**").authenticated()
                // Lo demás se niega por defecto.
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> escribir(res, 401, "No autenticado: falta un token válido"))
                .accessDeniedHandler((req, res, ex) -> escribir(res, 403, "No tiene permiso para esta operación")))
            .addFilterBefore(new JwtAuthenticationFilter(validador), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CuentaActivaFilter(perfiles), JwtAuthenticationFilter.class);
        return http.build();
    }

    /** CORS solo si se configuran orígenes (CORS_ORIGENES). Con el nginx del despliegue todo es del mismo origen y no hace falta. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cors.origenes:}") String origenes) {
        CorsConfiguration c = new CorsConfiguration();
        if (!origenes.isBlank()) {
            c.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList());
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        }
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", c);
        return fuente;
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, int estado, String mensaje)
            throws java.io.IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + estado + ",\"mensaje\":\"" + mensaje + "\"}");
    }
}
