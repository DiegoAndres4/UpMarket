package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Quien se registró con un correo solo puede publicar con ese correo: lo que mande el cliente se ignora. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "fotos.directorio=target/fotos-prueba"})
class CorreoDeContactoTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return "Bearer " + Jwts.builder().subject(usuario).claim("uid", 7).claim("roles", List.of(roles))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    @Test
    @DisplayName("El correo de contacto es siempre el de la cuenta, aunque el cuerpo traiga otro")
    void correoDeLaCuenta() throws Exception {
        mvc.perform(post("/api/categorias").header("Authorization", token("admin", "ADMIN", "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Categoria correo\"}"));
        String cuerpo = "{\"titulo\":\"Libro de prueba\",\"descripcion\":\"Descripcion de prueba\",\"precio\":\"5000\","
                + "\"estado\":\"usado\",\"categoria\":\"Categoria correo\",\"fotos\":[],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"otro@falso.com\"}";
        var r = mvc.perform(post("/api/publicaciones").header("Authorization", token("duena@correo.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn();
        int id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/publicaciones/" + id + "/contacto").header("Authorization", token("comprador@correo.com", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("duena@correo.com"))
                .andExpect(jsonPath("$.whatsapp").value("3001234567"));
    }
}
