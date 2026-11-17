package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw=="})
class ResenaRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1000);

    @Autowired
    private MockMvc mvc;

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 99).claim("roles", List.of(roles))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token, String json) {
        MockHttpServletRequestBuilder b = peticion.header("Authorization", "Bearer " + token);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String unico(String base) {
        return base + SECUENCIA.incrementAndGet();
    }

    private String cuerpo(int estrellas, String comentario) {
        return "{\"estrellas\":" + estrellas + ",\"comentario\":\"" + comentario + "\"}";
    }

    @Test
    @DisplayName("Crea una reseña y devuelve el autor tomado del token")
    void creaResena() throws Exception {
        String vendedor = unico("vendedor");
        String autor = unico("autor");

        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), token(autor, "USER"),
                        cuerpo(5, "Excelente vendedor")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor", is(autor)))
                .andExpect(jsonPath("$.autorNombre", is(autor)))
                .andExpect(jsonPath("$.estrellas", is(5)))
                .andExpect(jsonPath("$.comentario", is("Excelente vendedor")));
    }

    @Test
    @DisplayName("Una segunda reseña del mismo autor responde 409")
    void duplicadaEs409() throws Exception {
        String vendedor = unico("vendedor");
        String autor = unico("autor");
        String t = token(autor, "USER");

        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), t, cuerpo(4, "Buena atención")))
                .andExpect(status().isCreated());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), t, cuerpo(2, "Cambio de opinión")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Calificarse a sí mismo responde 400")
    void autoResenaEs400() throws Exception {
        String persona = unico("persona");

        mvc.perform(con(post("/api/perfil/" + persona + "/resenas"), token(persona, "USER"),
                        cuerpo(5, "Me califico solo")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Estrellas fuera de 0 a 5 y comentario corto responden 400")
    void datosInvalidosSon400() throws Exception {
        String vendedor = unico("vendedor");
        String autor = unico("autor");
        String t = token(autor, "USER");

        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), t, cuerpo(6, "Comentario válido")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), t, cuerpo(-1, "Comentario válido")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), t, cuerpo(5, "ok")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sin token responde 401")
    void sinTokenEs401() throws Exception {
        mvc.perform(post("/api/perfil/" + unico("vendedor") + "/resenas")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(5, "Comentario")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El listado trae promedio, total y reseñas")
    void listaPromedioYTotal() throws Exception {
        String vendedor = unico("vendedor");

        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), token(unico("autor"), "USER"),
                        cuerpo(5, "Muy bueno")))
                .andExpect(status().isCreated());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), token(unico("autor"), "USER"),
                        cuerpo(3, "Regular")))
                .andExpect(status().isCreated());

        mvc.perform(con(get("/api/perfil/" + vendedor + "/resenas"), token(unico("lector"), "USER"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor", is(vendedor)))
                .andExpect(jsonPath("$.promedio", is(4.0)))
                .andExpect(jsonPath("$.total", is(2)))
                .andExpect(jsonPath("$.resenas", hasSize(2)));
    }

    @Test
    @DisplayName("Un vendedor sin reseñas responde promedio 0 y lista vacía")
    void listadoVacio() throws Exception {
        mvc.perform(con(get("/api/perfil/" + unico("vendedor") + "/resenas"), token(unico("lector"), "USER"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio", is(0.0)))
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.resenas", hasSize(0)));
    }
}
