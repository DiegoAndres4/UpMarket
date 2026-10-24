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
 * Prueba de punta a punta (HTTP -> casos de uso -> dominio -> H2) con seguridad JWT real.
 * El secreto de abajo es SOLO de pruebas (48 bytes aleatorios en Base64); no es el de AuthCore.
 * Cada prueba usa títulos, categorías y personas propios (por eso el contador), para no depender del orden de ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw=="})
class UpmarketRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SECUENCIA = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 99).claim("roles", List.of(roles))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private String admin() { return token("admin", "ADMIN", "USER"); }

    private String estudiante(String nombre) { return token(nombre, "USER"); }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token, String json) {
        MockHttpServletRequestBuilder b = peticion.header("Authorization", "Bearer " + token);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String unico(String base) { return base + SECUENCIA.incrementAndGet(); }

    private String json(String titulo, String precio, String categoria) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\",\"precio\":\"" + precio
                + "\",\"estado\":\"usado\",\"categoria\":\"" + categoria + "\","
                + "\"fotos\":[{\"url\":\"https://archivos.example/a.jpg\",\"tipo\":\"image/jpeg\",\"tamanoBytes\":200000}],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}";
    }

    /** Publica como {@code quien} y devuelve el id. */
    private long publicar(String quien, String titulo, String precio, String categoria) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), estudiante(quien), json(titulo, precio, categoria)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    @Test
    @DisplayName("Sin token o con token falso: 401")
    void sinCredencialesEs401() throws Exception {
        mvc.perform(get("/api/publicaciones")).andExpect(status().isUnauthorized());
        mvc.perform(con(get("/api/categorias"), "token.falso.aqui", null)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Las categorías iniciales existen desde el arranque; crear es solo del ADMIN; duplicada 409; lo que no es /api está denegado")
    void categorias() throws Exception {
        mvc.perform(con(get("/api/categorias"), estudiante("maria"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItems("Tecnología", "Libros", "Útiles académicos", "Alimentos", "Servicios", "Otros")));
        String nombre = unico("Deportes");
        mvc.perform(con(post("/api/categorias"), estudiante("maria"), "{\"nombre\":\"" + nombre + "\"}")).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/categorias"), admin(), "{\"nombre\":\"" + nombre + "\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value(nombre));
        mvc.perform(con(post("/api/categorias"), admin(), "{\"nombre\":\"" + nombre.toUpperCase() + "\"}")).andExpect(status().isConflict());
        mvc.perform(con(post("/api/categorias"), admin(), "{}")).andExpect(status().isBadRequest());
        mvc.perform(con(get("/otra/cosa"), admin(), null)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Publicar: 201 con el vendedor del token; faltantes 400 con todos los campos; precio inválido y categoría inexistente")
    void publicarYValidar() throws Exception {
        String titulo = unico("Calculadora ");
        mvc.perform(con(post("/api/publicaciones"), estudiante("maria"), json(titulo, "50000", "tecnologia")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendedor").value("maria"))
                .andExpect(jsonPath("$.categoria").value("Tecnología"))
                .andExpect(jsonPath("$.estadoPublicacion").value("DISPONIBLE"))
                .andExpect(jsonPath("$.contacto").doesNotExist());
        mvc.perform(con(post("/api/publicaciones"), estudiante("maria"), "{\"whatsapp\":\"3001234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("título")))
                .andExpect(jsonPath("$.mensaje").value(containsString("descripción")))
                .andExpect(jsonPath("$.mensaje").value(containsString("precio")))
                .andExpect(jsonPath("$.mensaje").value(containsString("categoría")));
        mvc.perform(con(post("/api/publicaciones"), estudiante("maria"), json(unico("Malo "), "-5", "Libros")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El precio debe ser un número mayor que cero"));
        mvc.perform(con(post("/api/publicaciones"), estudiante("maria"), json(unico("Sin cat "), "100", "Inexistente")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Buscar: palabra clave sin tildes, filtros combinados, solo disponibles, y rango inválido 400")
    void buscar() throws Exception {
        String marca = unico("zafiro");
        long a = publicar("maria", "Portátil " + marca, "900000", "Tecnología");
        publicar("jose", "Libro de cálculo " + marca, "80000", "Libros");
        mvc.perform(con(get("/api/publicaciones?texto=" + marca), estudiante("ana"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(con(get("/api/publicaciones?texto=" + marca + "&categoria=libros&precioMax=100000"), estudiante("ana"), null))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].titulo").value(containsString("cálculo")));
        mvc.perform(con(get("/api/publicaciones?q=CALCULO " + marca), estudiante("ana"), null)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(con(get("/api/publicaciones?texto=" + marca + "&precioMin=500&precioMax=100"), estudiante("ana"), null))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post("/api/publicaciones/" + a + "/sold"), estudiante("maria"), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/publicaciones?texto=" + marca), estudiante("ana"), null)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Detalle sin contacto; contacto aparte; vendida: se ve pero no se contacta (409); inexistente 404")
    void detalleYContacto() throws Exception {
        long id = publicar("maria", unico("Bicicleta "), "300000", "Otros");
        mvc.perform(con(get("/api/publicaciones/" + id), estudiante("jose"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor").value("maria")).andExpect(jsonPath("$.whatsapp").doesNotExist());
        mvc.perform(con(get("/api/publicaciones/" + id + "/contacto"), estudiante("jose"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("3001234567")).andExpect(jsonPath("$.correo").value("vendedor@correo.com"));
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), estudiante("maria"), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/publicaciones/" + id), estudiante("jose"), null)).andExpect(jsonPath("$.estadoPublicacion").value("VENDIDA"));
        mvc.perform(con(get("/api/publicaciones/" + id + "/contacto"), estudiante("jose"), null)).andExpect(status().isConflict());
        mvc.perform(con(get("/api/publicaciones/999999"), estudiante("jose"), null)).andExpect(status().isNotFound());
        mvc.perform(con(get("/api/publicaciones/999999/contacto"), estudiante("jose"), null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Editar, vender y eliminar: solo el autor (403 también al ADMIN); 404 antes que 403; vendida no se edita (409)")
    void gestionDelAutor() throws Exception {
        long id = publicar("maria", unico("Mesa "), "120000", "Otros");
        String nuevo = "{\"titulo\":\"Mesa de estudio\",\"descripcion\":\"Con cajón\",\"precio\":\"100000\",\"estado\":\"USADO\",\"categoria\":\"Otros\"}";
        mvc.perform(con(put("/api/publicaciones/" + id), estudiante("jose"), nuevo)).andExpect(status().isForbidden());
        mvc.perform(con(put("/api/publicaciones/" + id), admin(), nuevo)).andExpect(status().isForbidden());
        mvc.perform(con(put("/api/publicaciones/999999"), estudiante("jose"), nuevo)).andExpect(status().isNotFound());
        mvc.perform(con(put("/api/publicaciones/" + id), estudiante("maria"), nuevo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Mesa de estudio")).andExpect(jsonPath("$.precio").value(100000.0));
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), estudiante("jose"), null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), estudiante("maria"), null)).andExpect(status().isOk());
        mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), estudiante("maria"), null)).andExpect(status().isConflict());
        mvc.perform(con(put("/api/publicaciones/" + id), estudiante("maria"), nuevo)).andExpect(status().isConflict());
        mvc.perform(con(delete("/api/publicaciones/" + id), estudiante("jose"), null)).andExpect(status().isForbidden());
        mvc.perform(con(delete("/api/publicaciones/" + id), estudiante("maria"), null)).andExpect(status().isNoContent());
        mvc.perform(con(get("/api/publicaciones/" + id), estudiante("maria"), null)).andExpect(status().isNotFound());
        mvc.perform(con(delete("/api/publicaciones/" + id), estudiante("maria"), null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Mis publicaciones: solo las mías, vendidas incluidas")
    void mias() throws Exception {
        String persona = unico("vendedora");
        long a = publicar(persona, unico("Uno "), "1000", "Otros");
        publicar(persona, unico("Dos "), "2000", "Otros");
        publicar("otra", unico("Ajena "), "3000", "Otros");
        mvc.perform(con(post("/api/publicaciones/" + a + "/sold"), estudiante(persona), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/publicaciones/mias"), estudiante(persona), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].vendedor", everyItem(is(persona))))
                .andExpect(jsonPath("$[*].estadoPublicacion", hasItems("VENDIDA", "DISPONIBLE")));
    }
}
