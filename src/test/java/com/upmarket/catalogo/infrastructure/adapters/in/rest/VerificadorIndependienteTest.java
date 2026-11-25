package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Interesado;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.InteresSpringJpaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Pruebas de regresión de la verificación independiente de interesados. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "spring.datasource.url=jdbc:h2:mem:verificador;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class VerificadorIndependienteTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @Autowired private MockMvc mvc;
    @Autowired private InteresSpringJpaRepository intereses;
    @Autowired private PublicacionUseCase publicaciones;

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 99).claim("roles", List.of(roles))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }
    private String admin() { return token("admin", "ADMIN", "USER"); }
    private String user(String u) { return token(u, "USER"); }
    private String unico(String b) { return b + SEQ.incrementAndGet(); }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder p, String tok, String json) {
        MockHttpServletRequestBuilder b = p.header("Authorization", "Bearer " + tok);
        return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String json(String titulo) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\",\"precio\":\"50000\","
                + "\"estado\":\"usado\",\"categoria\":\"Otros\",\"fotos\":[],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}";
    }

    private long publicarPendiente(String quien, String titulo) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), user(quien), json(titulo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long publicar(String quien, String titulo) throws Exception {
        long id = publicarPendiente(quien, titulo);
        mvc.perform(con(post("/api/publicaciones/" + id + "/pago"), user(quien), null)).andExpect(status().isOk());
        return id;
    }

    private void perfil(String usuario, String nombre, String telefono) throws Exception {
        mvc.perform(con(put("/api/perfil"), user(usuario),
                        "{\"nombre\":\"" + nombre + "\",\"telefono\":\"" + telefono + "\",\"fotoUrl\":null}"))
                .andExpect(status().isOk());
    }

    private MvcResult pedir(String path, String tok) throws Exception {
        return mvc.perform(con(get(path), tok, null)).andReturn();
    }

    private int filas(long id) {
        return intereses.findByPublicacionIdOrderByUltimaVezDesc(id).size();
    }

    private List<String> foto(long id) {
        return intereses.findByPublicacionIdOrderByUltimaVezDesc(id).stream()
                .map(e -> { Interesado i = e.aDominio(); return i.usuario() + "/" + i.veces() + "/" + i.ultimaVez(); })
                .toList();
    }

    @Test
    @DisplayName("S1: registra al pedir contacto, deduplica veces/ultimaVez, autor y ADMIN no cuentan")
    void s1RegistroYDedup() throws Exception {
        String vendedor = unico("vend"), comprador = unico("comp");
        long id = publicar(vendedor, unico("Bici "));
        perfil(comprador, "Compra Uno", "300 123 4567");
        assertEquals(0, filas(id));

        MvcResult r = pedir("/api/publicaciones/" + id + "/contacto", user(comprador));
        assertEquals(200, r.getResponse().getStatus());
        String cuerpo = r.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("\"whatsapp\":\"3001234567\""));
        assertTrue(cuerpo.contains("\"correo\":\"vendedor@correo.com\""));
        assertEquals(1, filas(id));
        String primeraVez = foto(id).get(0);

        MvcResult lista1 = pedir("/api/publicaciones/" + id + "/interesados", user(vendedor));
        assertEquals(200, lista1.getResponse().getStatus());
        String cuerpoLista = lista1.getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(cuerpoLista, "$");
        assertEquals(1, items.size());
        assertEquals(new HashSet<>(List.of("nombre", "telefono", "veces", "ultimaVez")), items.get(0).keySet());
        assertEquals("Compra Uno", items.get(0).get("nombre"));
        assertEquals("3001234567", items.get(0).get("telefono"));
        assertEquals(1, ((Number) items.get(0).get("veces")).intValue());
        assertFalse(cuerpoLista.contains("\"usuario\""));
        assertFalse(cuerpoLista.contains("comprador\""), "no debe exponer el usuario del interesado");

        Thread.sleep(30);
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(1, filas(id), "no debe duplicar la fila");
        String segundaVez = foto(id).get(0);
        assertNotEquals(primeraVez, segundaVez);
        assertTrue(segundaVez.endsWith("/2/" + segundaVez.substring(segundaVez.lastIndexOf('/') + 1)));
        assertTrue(segundaVez.contains("/2/"));

        List<Map<String, Object>> items2 = JsonPath.read(
                pedir("/api/publicaciones/" + id + "/interesados", user(vendedor)).getResponse().getContentAsString(), "$");
        assertEquals(1, items2.size());
        assertEquals(2, ((Number) items2.get(0).get("veces")).intValue());

        // autor y ADMIN piden contacto: 200 pero no se registran
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(vendedor)).getResponse().getStatus());
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", admin()).getResponse().getStatus());
        assertEquals(1, filas(id));
        assertEquals(2, ((Number) JsonPath.read(
                pedir("/api/publicaciones/" + id + "/interesados", user(vendedor)).getResponse().getContentAsString(),
                "$[0].veces")).intValue());
    }

    @Test
    @DisplayName("S1: vendida, sin pago, inexistente y sin token no registran y no mutan datos")
    void s1RechazosNoMutan() throws Exception {
        String vendedor = unico("vend"), comprador = unico("comp"), otro = unico("otro");
        long id = publicar(vendedor, unico("Bici "));
        perfil(comprador, "Uno", "3001234567");
        perfil(otro, "Dos", "3109998888");
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(comprador)).getResponse().getStatus());
        List<String> antes = foto(id);

        assertEquals(200, pedir("/api/publicaciones/" + id, user(vendedor)).getResponse().getStatus());
        assertEquals(200, mvc.perform(con(post("/api/publicaciones/" + id + "/sold"), user(vendedor), null))
                .andReturn().getResponse().getStatus());
        MvcResult vendida = pedir("/api/publicaciones/" + id + "/contacto", user(otro));
        assertEquals(409, vendida.getResponse().getStatus());
        assertEquals(antes, foto(id), "un 409 no debe mutar los interesados");

        // publicación sin pago: 404 y sin fila
        long pendiente = publicarPendiente(vendedor, unico("Pendiente "));
        assertEquals(404, pedir("/api/publicaciones/" + pendiente + "/contacto", user(otro)).getResponse().getStatus());
        assertEquals(0, filas(pendiente));

        // inexistente
        assertEquals(404, pedir("/api/publicaciones/999999/contacto", user(otro)).getResponse().getStatus());

        // sin token
        assertEquals(401, mvc.perform(get("/api/publicaciones/" + id + "/contacto")).andReturn().getResponse().getStatus());
        assertEquals(antes, foto(id));

        // tercero y ADMIN no pueden listar (403)
        assertEquals(403, pedir("/api/publicaciones/" + id + "/interesados", user(otro)).getResponse().getStatus());
        assertEquals(403, pedir("/api/publicaciones/" + id + "/interesados", admin()).getResponse().getStatus());
        assertEquals(404, pedir("/api/publicaciones/999999/interesados", user(vendedor)).getResponse().getStatus());
        assertEquals(antes, foto(id), "un 403 no debe mutar los interesados");

        // autor de una publicación pendiente: lista vacía 200
        MvcResult vacia = pedir("/api/publicaciones/" + pendiente + "/interesados", user(vendedor));
        assertEquals(200, vacia.getResponse().getStatus());
        assertEquals("[]", vacia.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("S2/S3: orden por ultimaVez, fallback sin perfil, sin teléfono, perfil público oculta celular")
    void s2OrdenYPrivacidad() throws Exception {
        String vendedor = unico("vend"), viejo = unico("viejo"), nuevo = unico("nuevo");
        long id = publicar(vendedor, unico("Libro "));
        perfil(viejo, "Interesado Viejo", "3001112222");
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(viejo)).getResponse().getStatus());
        Thread.sleep(60);
        String correoNuevo = nuevo + "@correo.example";
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(correoNuevo)).getResponse().getStatus());

        MvcResult r = pedir("/api/publicaciones/" + id + "/interesados", user(vendedor));
        String cuerpo = r.getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(cuerpo, "$");
        assertEquals(2, items.size());
        assertEquals(nuevo, items.get(0).get("nombre"), "el más reciente va primero");
        assertEquals(new HashSet<>(List.of("nombre", "veces", "ultimaVez")), items.get(0).keySet(),
                "sin teléfono el campo no debe aparecer");
        assertEquals("Interesado Viejo", items.get(1).get("nombre"));
        assertEquals("3001112222", items.get(1).get("telefono"));
        assertFalse(cuerpo.contains("@correo.example"), "nunca debe exponer el correo/usuario del interesado");

        // perfil público sigue sin teléfono
        MvcResult pub = pedir("/api/perfil/publico?usuario=" + viejo, user(vendedor));
        assertEquals(200, pub.getResponse().getStatus());
        String cuerpoPub = pub.getResponse().getContentAsString();
        assertFalse(cuerpoPub.contains("telefono"));
        assertFalse(cuerpoPub.contains("3001112222"));
        assertEquals(new HashSet<>(List.of("nombre", "fotoUrl")),
                ((Map<String, Object>) JsonPath.read(cuerpoPub, "$")).keySet());

        // el detalle tampoco expone el contacto
        String detalle = pedir("/api/publicaciones/" + id, user(viejo)).getResponse().getContentAsString();
        assertFalse(detalle.contains("3001234567"));

        // cambio de perfil se refleja en la lista
        perfil(viejo, "Viejo Renombrado", "310 999 8888");
        List<Map<String, Object>> items3 = JsonPath.read(
                pedir("/api/publicaciones/" + id + "/interesados", user(vendedor)).getResponse().getContentAsString(), "$");
        assertEquals("Viejo Renombrado", items3.get(1).get("nombre"));
        assertEquals("3109998888", items3.get(1).get("telefono"));
    }

    @Test
    @DisplayName("S2: archivada 404 para el autor; contacto archivado 404 sin registrar")
    void s2Archivada() throws Exception {
        String vendedor = unico("vend"), comprador = unico("comp");
        long id = publicar(vendedor, unico("Archivable "));
        perfil(comprador, "Comp", "3001234567");
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(1, filas(id));

        publicaciones.archivarDelUsuario(Actor.de(vendedor, List.of("USER")));

        assertEquals(404, pedir("/api/publicaciones/" + id + "/interesados", user(vendedor)).getResponse().getStatus());
        assertEquals(1, filas(id), "la lista 404 no debe borrar filas");
        assertEquals(404, pedir("/api/publicaciones/" + id + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(1, filas(id), "el contacto 404 no debe registrar ni borrar");
    }

    @Test
    @DisplayName("S5: eliminar publicación limpia interesados (autor y ADMIN)")
    void s5EliminarLimpia() throws Exception {
        String vendedor = unico("vend"), comprador = unico("comp");
        long id = publicar(vendedor, unico("Borrable "));
        perfil(comprador, "Comp", "3001234567");
        assertEquals(200, pedir("/api/publicaciones/" + id + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(1, filas(id));
        assertEquals(204, mvc.perform(con(delete("/api/publicaciones/" + id), user(vendedor), null))
                .andReturn().getResponse().getStatus());
        assertEquals(0, filas(id), "el autor elimina y se limpian los interesados");
        assertEquals(404, pedir("/api/publicaciones/" + id + "/interesados", user(vendedor)).getResponse().getStatus());

        // ADMIN elimina la publicación de otro: también limpia
        String vendedor2 = unico("vend"), comprador2 = unico("comp");
        long id2 = publicar(vendedor2, unico("BorrableAdmin "));
        perfil(comprador2, "Comp2", "3001234567");
        assertEquals(200, pedir("/api/publicaciones/" + id2 + "/contacto", user(comprador2)).getResponse().getStatus());
        assertEquals(1, filas(id2));
        assertEquals(204, mvc.perform(con(delete("/api/publicaciones/" + id2), admin(), null))
                .andReturn().getResponse().getStatus());
        assertEquals(0, filas(id2));
    }

    @Test
    @DisplayName("S6: contacto y detalle existentes mantienen forma y contenido")
    void s6Regresion() throws Exception {
        String vendedor = unico("vend"), otro = unico("otro");
        long id = publicar(vendedor, unico("Regresion "));
        MvcResult detalle = pedir("/api/publicaciones/" + id, user(otro));
        assertEquals(200, detalle.getResponse().getStatus());
        String cuerpoDetalle = detalle.getResponse().getContentAsString();
        assertFalse(cuerpoDetalle.contains("\"whatsapp\""));
        assertFalse(cuerpoDetalle.contains("\"correo\""));
        assertFalse(cuerpoDetalle.contains("\"contacto\""));

        MvcResult contacto = pedir("/api/publicaciones/" + id + "/contacto", user(otro));
        assertEquals(200, contacto.getResponse().getStatus());
        Map<String, Object> c = JsonPath.read(contacto.getResponse().getContentAsString(), "$");
        assertEquals(new HashSet<>(List.of("whatsapp", "correo")), c.keySet());

        assertEquals(200, mvc.perform(con(get("/api/publicaciones"), user(otro), null))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(con(get("/api/categorias"), user(otro), null))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(con(get("/api/perfil"), user(otro), null))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("S1: ver el detalle no registra; el contador es por publicación")
    void s1DetalleNoRegistraYContadoresIndependientes() throws Exception {
        String vendedor = unico("vend"), comprador = unico("comp");
        long p1 = publicar(vendedor, unico("Detalle1 "));
        long p2 = publicar(vendedor, unico("Detalle2 "));
        perfil(comprador, "Comp", "3001234567");
        for (int i = 0; i < 3; i++) {
            assertEquals(200, pedir("/api/publicaciones/" + p1, user(comprador)).getResponse().getStatus());
        }
        assertEquals(0, filas(p1), "ver el detalle no debe registrar interés");

        assertEquals(200, pedir("/api/publicaciones/" + p1 + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(200, pedir("/api/publicaciones/" + p2 + "/contacto", user(comprador)).getResponse().getStatus());
        assertEquals(1, filas(p1));
        assertEquals(1, filas(p2));
        assertEquals(1, ((Number) JsonPath.read(
                pedir("/api/publicaciones/" + p1 + "/interesados", user(vendedor)).getResponse().getContentAsString(),
                "$[0].veces")).intValue());
        assertEquals(1, ((Number) JsonPath.read(
                pedir("/api/publicaciones/" + p2 + "/interesados", user(vendedor)).getResponse().getContentAsString(),
                "$[0].veces")).intValue());
    }
}
