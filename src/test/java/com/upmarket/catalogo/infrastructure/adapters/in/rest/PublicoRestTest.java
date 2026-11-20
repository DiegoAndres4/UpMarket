package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

/**
 * La cartelera pública (landing). Sin token: solo lectura y solo los campos del aviso.
 * Usa títulos y categorías propios para no depender del orden de ejecución de las pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw=="})
class PublicoRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SECUENCIA = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;

    private String token(String usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 77).claim("roles", List.of("USER"))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String json) {
        MockHttpServletRequestBuilder b = peticion.header("Authorization", "Bearer " + token("publico"));
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String unico(String base) { return base + SECUENCIA.incrementAndGet(); }

    private String json(String titulo, String precio, String categoria) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\",\"precio\":\"" + precio
                + "\",\"estado\":\"usado\",\"categoria\":\"" + categoria + "\","
                + "\"fotos\":[{\"url\":\"https://archivos.example/cartelera.jpg\",\"tipo\":\"image/jpeg\",\"tamanoBytes\":200000}],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}";
    }

    private long publicarPendiente(String titulo, String precio, String categoria) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), json(titulo, precio, categoria)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long publicar(String titulo, String precio, String categoria) throws Exception {
        long id = publicarPendiente(titulo, precio, categoria);
        mvc.perform(con(post("/api/publicaciones/" + id + "/pago"), null)).andExpect(status().isOk());
        return id;
    }

    @Test
    @DisplayName("La cartelera se lee sin token y expone solo los campos del aviso")
    void carteleraAnonimaExponeSoloCamposPublicos() throws Exception {
        String titulo = unico("Cartelera pública ");
        long id = publicar(titulo, "2400000", "tecnologia");

        mvc.perform(get("/api/publico/cartelera").param("texto", titulo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].titulo").value(titulo))
                .andExpect(jsonPath("$[0].precio").value(2400000.0))
                .andExpect(jsonPath("$[0].categoria").value("Tecnología"))
                .andExpect(jsonPath("$[0].estadoArticulo").value("USADO"))
                .andExpect(jsonPath("$[0].fotoUrl").value("https://archivos.example/cartelera.jpg"))
                .andExpect(jsonPath("$[0].creadoEn").exists())
                .andExpect(jsonPath("$[0].descripcion").doesNotExist())
                .andExpect(jsonPath("$[0].fotos").doesNotExist())
                .andExpect(jsonPath("$[0].vendedor").doesNotExist())
                .andExpect(jsonPath("$[0].estadoPago").doesNotExist())
                .andExpect(jsonPath("$[0].estadoPublicacion").doesNotExist())
                .andExpect(jsonPath("$[0].vistas").doesNotExist())
                .andExpect(jsonPath("$[0].contacto").doesNotExist())
                .andExpect(jsonPath("$[0].whatsapp").doesNotExist())
                .andExpect(jsonPath("$[0].correo").doesNotExist());
    }

    @Test
    @DisplayName("Pendientes de pago y vendidas no aparecen en la cartelera")
    void excluyePendientesDePagoYVendidas() throws Exception {
        String pendiente = unico("Pendiente de pago ");
        publicarPendiente(pendiente, "10000", "libros");
        mvc.perform(get("/api/publico/cartelera").param("texto", pendiente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        String vendida = unico("Ya vendida ");
        long id = publicar(vendida, "20000", "libros");
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), null)).andExpect(status().isOk());
        mvc.perform(get("/api/publico/cartelera").param("texto", vendida))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Filtros: categoría, límite válido, categoría desconocida vacía y orden más reciente primero")
    void filtraPorCategoriaYLimita() throws Exception {
        String texto = unico("Filtro cartelera ");
        long primero = publicar(texto + " uno", "30000", "libros");
        long segundo = publicar(texto + " dos", "40000", "tecnologia");

        mvc.perform(get("/api/publico/cartelera").param("texto", texto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(segundo))
                .andExpect(jsonPath("$[1].id").value(primero));

        mvc.perform(get("/api/publico/cartelera").param("texto", texto).param("categoria", "tecnologia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(segundo));

        mvc.perform(get("/api/publico/cartelera").param("texto", texto).param("limite", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(get("/api/publico/cartelera").param("texto", texto).param("categoria", "NoExisteXYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Límite fuera de 1..24 y texto de más de 80 caracteres: 400 con el formato del proyecto")
    void parametrosInvalidosSon400() throws Exception {
        mvc.perform(get("/api/publico/cartelera").param("limite", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").exists());
        mvc.perform(get("/api/publico/cartelera").param("limite", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/publico/cartelera").param("texto", "x".repeat(81)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Las categorías se leen sin token")
    void categoriasPublicas() throws Exception {
        mvc.perform(get("/api/publico/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Tecnología")))
                .andExpect(jsonPath("$", hasItem("Libros")));
    }

    @Test
    @DisplayName("La cartelera pública es solo de lectura: POST, PUT y DELETE anónimos no se permiten")
    void soloLectura() throws Exception {
        mvc.perform(post("/api/publico/cartelera")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/publico/cartelera")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/publico/cartelera")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/publico/categorias")).andExpect(status().is4xxClientError());
    }
}
