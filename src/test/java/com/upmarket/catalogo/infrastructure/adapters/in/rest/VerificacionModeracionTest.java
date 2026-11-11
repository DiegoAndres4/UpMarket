package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Pruebas de escalada de privilegios y de la moderación por ADMIN (DELETE de publicaciones ajenas). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw=="})
class VerificacionModeracionTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @Autowired private MockMvc mvc;

    private String token(String usuario, String... roles) {
        Instant a = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 99).claim("roles", List.of(roles))
                .issuedAt(Date.from(a)).expiration(Date.from(a.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }
    private String admin() { return token("admin", "ADMIN", "USER"); }
    private String user(String u) { return token(u, "USER"); }
    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder b, String t, String json) {
        b = b.header("Authorization", "Bearer " + t);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    private String u(String base) { return base + SEQ.incrementAndGet(); }
    private String json(String titulo) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de sonda\",\"precio\":\"12345\","
                + "\"estado\":\"usado\",\"categoria\":\"Otros\",\"fotos\":[],\"whatsapp\":\"3001234567\","
                + "\"correo\":\"sonda@correo.com\"}";
    }
    private long publicar(String quien, String titulo) throws Exception {
        MvcResult r = mvc.perform(con(post("/api/publicaciones"), user(quien), json(titulo)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }
    private String detalle(long id, String quien) throws Exception {
        return mvc.perform(con(get("/api/publicaciones/" + id), user(quien), null))
                .andReturn().getResponse().getContentAsString();
    }

    @Test @DisplayName("S1 tercero no borra: 403 y el recurso queda byte-igual")
    void terceroNoBorra() throws Exception {
        long id = publicar("duena", u("Sonda tercera "));
        String antes = detalle(id, "duena");
        mvc.perform(con(delete("/api/publicaciones/" + id), user("intruso"), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo el autor puede modificar esta publicación"));
        assertEquals(antes, detalle(id, "duena"));
        mvc.perform(con(get("/api/publicaciones/" + id + "/contacto"), user("intruso"), null)).andExpect(status().isOk());
    }

    @Test @DisplayName("S2 ADMIN borra ajena: 204, 404 después y fuera de búsqueda")
    void adminBorraAjena() throws Exception {
        String marca = u("Sonda moderada ");
        long id = publicar("duena", marca);
        String cuerpo = detalle(id, "duena");
        String titulo = JsonPath.read(cuerpo, "$.titulo");
        mvc.perform(con(get("/api/publicaciones?texto=" + titulo), user("duena"), null))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(con(delete("/api/publicaciones/" + id), admin(), null)).andExpect(status().isNoContent());
        mvc.perform(con(get("/api/publicaciones/" + id), user("duena"), null)).andExpect(status().isNotFound());
        mvc.perform(con(get("/api/publicaciones?texto=" + titulo), user("duena"), null))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test @DisplayName("S3 ADMIN no edita ni vende ajenas; estado intacto")
    void adminNoEditaNiVende() throws Exception {
        long id = publicar("duena", u("Sonda edicion "));
        String antes = detalle(id, "duena");
        String nuevo = "{\"titulo\":\"Cambiado\",\"descripcion\":\"Cambiado\",\"precio\":\"999\",\"estado\":\"nuevo\",\"categoria\":\"Otros\"}";
        mvc.perform(con(put("/api/publicaciones/" + id), admin(), nuevo)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), admin(), null)).andExpect(status().isForbidden());
        assertEquals(antes, detalle(id, "duena"));
    }

    @Test @DisplayName("S4 autor sigue borrando; ADMIN borra la propia; 404 antes que 403")
    void autorYOrden() throws Exception {
        long id = publicar("duena", u("Sonda autora "));
        mvc.perform(con(delete("/api/publicaciones/" + id), user("duena"), null)).andExpect(status().isNoContent());
        mvc.perform(con(delete("/api/publicaciones/" + id), user("duena"), null)).andExpect(status().isNotFound());
        mvc.perform(con(delete("/api/publicaciones/999999"), user("intruso"), null)).andExpect(status().isNotFound());
        long propia = publicar("admin", u("Sonda admin propia "));
        mvc.perform(con(delete("/api/publicaciones/" + propia), admin(), null)).andExpect(status().isNoContent());
    }

    @Test @DisplayName("S4b rol solo del token firmado: sin token 401, firma falsa 401, encabezados/cuerpo no escalan")
    void rolSoloDelToken() throws Exception {
        long id = publicar("duena", u("Sonda rol "));
        mvc.perform(delete("/api/publicaciones/" + id)).andExpect(status().isUnauthorized());
        String falsificado = Jwts.builder().subject("intruso").claim("roles", List.of("ADMIN"))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==")),
                        Jwts.SIG.HS384).compact();
        mvc.perform(con(delete("/api/publicaciones/" + id), falsificado, null)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/publicaciones/" + id)
                        .header("Authorization", "Bearer " + user("intruso"))
                        .header("X-Roles", "ADMIN").header("X-Role", "ADMIN")
                        .param("admin", "true").param("roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(con(delete("/api/publicaciones/" + id), user("intruso"), null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/publicaciones/" + id), user("duena"), null)).andExpect(status().isOk());
    }

    @Test @DisplayName("S4c ROLE_ADMIN del token funciona; rol desconocido no; borrada no se edita ni vende")
    void normalizacionYConsecuencias() throws Exception {
        long id = publicar("duena", u("Sonda normalizada "));
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), token("mod", "ROLE_ADMIN"), null))
                .andExpect(status().isForbidden());
        mvc.perform(con(delete("/api/publicaciones/" + id), token("mod", "ROLE_ADMIN"), null)).andExpect(status().isNoContent());
        long otra = publicar("duena", u("Sonda desconocida "));
        mvc.perform(con(delete("/api/publicaciones/" + otra), token("intruso", "SUPERADMIN"), null))
                .andExpect(status().isForbidden());
        long borrada = publicar("duena", u("Sonda consecuencias "));
        mvc.perform(con(delete("/api/publicaciones/" + borrada), admin(), null)).andExpect(status().isNoContent());
        String nuevo = "{\"titulo\":\"X\",\"descripcion\":\"X\",\"precio\":\"1\",\"estado\":\"nuevo\",\"categoria\":\"Otros\"}";
        mvc.perform(con(put("/api/publicaciones/" + borrada), admin(), nuevo)).andExpect(status().isNotFound());
        mvc.perform(con(post("/api/publicaciones/" + borrada + "/sold"), admin(), null)).andExpect(status().isNotFound());
    }
}
