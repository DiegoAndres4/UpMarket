package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Perfil: ver, editar (celular colombiano completo), perfil público sin celular y eliminar la cuenta. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "fotos.directorio=target/fotos-prueba"})
class PerfilRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;

    private String token(String usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 5).claim("roles", List.of("USER"))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    @Test
    @DisplayName("Un usuario nuevo recibe un perfil con el nombre sacado de su correo")
    void perfilPorDefecto() throws Exception {
        mvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token("camila.rojas@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("camila.rojas"))
                .andExpect(jsonPath("$.usuario").value("camila.rojas@correo.com"));
    }

    @Test
    @DisplayName("Editar el perfil: celular colombiano completo (200) o inválido (400)")
    void editarPerfil() throws Exception {
        String t = "Bearer " + token("luis@correo.com");
        mvc.perform(put("/api/perfil").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Perez\",\"telefono\":\"300 123 4567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luis Perez"))
                .andExpect(jsonPath("$.telefono").value("3001234567"));
        mvc.perform(put("/api/perfil").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Perez\",\"telefono\":\"30012345\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/perfil").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"L\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El perfil público muestra nombre y foto, pero no el celular")
    void perfilPublico() throws Exception {
        mvc.perform(put("/api/perfil").header("Authorization", "Bearer " + token("sofia@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sofia\",\"telefono\":\"3101234567\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/perfil/publico").param("usuario", "sofia@correo.com")
                        .header("Authorization", "Bearer " + token("otro@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sofia"))
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }

    @Test
    @DisplayName("Eliminar la cuenta borra sus datos y su token deja de servir (401)")
    void eliminarCuenta() throws Exception {
        String t = "Bearer " + token("borrar@correo.com");
        mvc.perform(get("/api/perfil").header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(delete("/api/perfil").header("Authorization", t)).andExpect(status().isNoContent());
        mvc.perform(get("/api/perfil").header("Authorization", t)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categorias").header("Authorization", t)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin token no se puede ver el perfil (401)")
    void sinToken() throws Exception {
        mvc.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
    }
}
