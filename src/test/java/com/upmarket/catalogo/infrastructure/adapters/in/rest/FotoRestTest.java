package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Subida y entrega de fotos: solo JPG/PNG por contenido, hasta 5 MB, subir exige token y ver es público. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "fotos.directorio=target/fotos-prueba"})
class FotoRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    private String token() {
        Instant ahora = Instant.now();
        return Jwts.builder().subject("ana").claim("uid", 1).claim("roles", List.of("USER"))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    @Test
    @DisplayName("Sin token no se puede subir una foto (401)")
    void sinTokenNoSube() throws Exception {
        mvc.perform(multipart("/api/fotos").file(new MockMultipartFile("archivo", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un PNG válido se guarda y se puede ver sin token")
    void subeYVe() throws Exception {
        var r = mvc.perform(multipart("/api/fotos").file(new MockMultipartFile("archivo", "a.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("image/png"))
                .andExpect(jsonPath("$.url", startsWith("/api/fotos/")))
                .andReturn();
        String url = JsonPath.read(r.getResponse().getContentAsString(), "$.url");
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    @Test
    void alQuitarUnaFotoPropiaSeEliminaTrasGuardar() throws Exception {
        var subida = mvc.perform(multipart("/api/fotos")
                        .file(new MockMultipartFile("archivo", "a.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isCreated()).andReturn();
        String url = JsonPath.read(subida.getResponse().getContentAsString(), "$.url");
        String foto = "{\"url\":\"" + url + "\",\"tipo\":\"image/png\",\"tamanoBytes\":12}";
        String base = "\"titulo\":\"Libro usado\",\"descripcion\":\"En buen estado\",\"precio\":\"10000\","
                + "\"estado\":\"USADO\",\"categoria\":\"Otros\"";
        var creada = mvc.perform(post("/api/publicaciones").header("Authorization", "Bearer " + token())
                        .contentType("application/json")
                        .content("{" + base + ",\"fotos\":[" + foto + "],\"correo\":\"ana@example.com\"}"))
                .andExpect(status().isCreated()).andReturn();
        Number id = JsonPath.read(creada.getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/publicaciones/" + id).header("Authorization", "Bearer " + token())
                        .contentType("application/json")
                        .content("{\"titulo\":\"x\",\"descripcion\":\"En buen estado\",\"precio\":\"10000\","
                                + "\"estado\":\"USADO\",\"categoria\":\"Otros\",\"fotos\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(put("/api/publicaciones/" + id).header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{" + base + ",\"fotos\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un archivo que no es JPG ni PNG se rechaza aunque diga serlo (400)")
    void rechazaOtroContenido() throws Exception {
        mvc.perform(multipart("/api/fotos")
                        .file(new MockMultipartFile("archivo", "a.png", "image/png", "no soy una imagen".getBytes()))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un nombre inventado o con rutas no devuelve nada (404)")
    void nombreInvalido() throws Exception {
        mvc.perform(get("/api/fotos/secreto.txt")).andExpect(status().isNotFound());
    }
}
