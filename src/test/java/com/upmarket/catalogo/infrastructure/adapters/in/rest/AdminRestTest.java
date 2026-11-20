package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PagoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PublicacionSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.ResenaSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Panel de administración por HTTP: seguridad por rol, estadísticas, listado con filtros y eliminación.
 * Usa una base H2 propia y la vacía antes de cada prueba para que las cifras sean exactas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "spring.datasource.url=jdbc:h2:mem:upmarket-admin-rest;MODE=MySQL;DB_CLOSE_DELAY=-1"})
class AdminRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final AtomicInteger SECUENCIA = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PagoSpringJpaRepository pagos;
    @Autowired
    private ResenaSpringJpaRepository resenas;
    @Autowired
    private PublicacionSpringJpaRepository publicaciones;
    @Autowired
    private PerfilRepository perfiles;

    @BeforeEach
    void limpiar() {
        pagos.deleteAll();
        resenas.deleteAll();
        publicaciones.deleteAll();
        perfiles.deleteAll();
    }

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 77).claim("roles", List.of(roles))
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

    private long publicarPendiente(String quien, String titulo, String categoria) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), estudiante(quien), json(titulo, "50000", categoria)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private void pagar(String quien, long id) throws Exception {
        mvc.perform(con(post("/api/publicaciones/" + id + "/pago"), estudiante(quien), null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin token 401; un usuario normal recibe 403 en todas las rutas del panel; el ADMIN entra")
    void soloElAdminEntraAlPanel() throws Exception {
        mvc.perform(get("/api/admin/estadisticas")).andExpect(status().isUnauthorized());
        String usuario = estudiante("ana.normal");
        mvc.perform(con(get("/api/admin/estadisticas"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/publicaciones"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/usuarios"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(delete("/api/admin/publicaciones/1"), usuario, null)).andExpect(status().isForbidden());

        mvc.perform(con(get("/api/admin/estadisticas"), admin(), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/admin/publicaciones"), admin(), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/admin/usuarios"), admin(), null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El ADMIN no puede publicar; un usuario normal sí")
    void adminNoPublica() throws Exception {
        mvc.perform(con(post("/api/publicaciones"), admin(), json(unico("Admin "), "50000", "Otros")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El administrador no publica artículos."));
        assertThat(publicaciones.count()).isZero();

        mvc.perform(con(post("/api/publicaciones"), estudiante("ana.publica"), json(unico("Ana "), "50000", "Otros")))
                .andExpect(status().isCreated());
        assertThat(publicaciones.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Estadísticas: totales por estado, pagos aprobados e ingresos, vistas, top 5, categorías, 30 días, perfiles y reseñas")
    void estadisticasReflejanLosDatos() throws Exception {
        String vendedor = "vendedor.stats@correo.com";
        String comprador = "comprador.stats";
        String marca = unico("stats");
        long pendiente = publicarPendiente(vendedor, "Pendiente " + marca, "Otros");
        long pagada = publicarPendiente(vendedor, "Pagada " + marca, "Otros");
        pagar(vendedor, pagada);
        long vendida = publicarPendiente(vendedor, "Vendida " + marca, "Libros");
        pagar(vendedor, vendida);
        mvc.perform(con(post("/api/publicaciones/" + vendida + "/sold"), estudiante(vendedor), null))
                .andExpect(status().isOk());
        mvc.perform(con(get("/api/publicaciones/" + pagada), estudiante(comprador), null))
                .andExpect(status().isOk());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), estudiante(comprador),
                "{\"estrellas\":5,\"comentario\":\"Muy buen vendedor\"}")).andExpect(status().isCreated());
        mvc.perform(con(get("/api/perfil"), estudiante(vendedor), null)).andExpect(status().isOk());

        MvcResult resultado = mvc.perform(con(get("/api/admin/estadisticas"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPublicaciones").value(3))
                .andExpect(jsonPath("$.publicacionesPorEstado.DISPONIBLE").value(2))
                .andExpect(jsonPath("$.publicacionesPorEstado.VENDIDA").value(1))
                .andExpect(jsonPath("$.publicacionesPorEstadoPago.PENDIENTE").value(1))
                .andExpect(jsonPath("$.publicacionesPorEstadoPago.APROBADO").value(2))
                .andExpect(jsonPath("$.publicacionesPorEstadoPago.RECHAZADO").value(0))
                .andExpect(jsonPath("$.pagosAprobados").value(2))
                .andExpect(jsonPath("$.ingresosAprobados").value(6000.0))
                .andExpect(jsonPath("$.vistasTotales").value(1))
                .andExpect(jsonPath("$.topPublicaciones", hasSize(3)))
                .andExpect(jsonPath("$.topPublicaciones[0].id").value(pagada))
                .andExpect(jsonPath("$.topPublicaciones[0].vistas").value(1))
                .andExpect(jsonPath("$.publicacionesPorCategoria.Otros").value(2))
                .andExpect(jsonPath("$.publicacionesPorCategoria.Libros").value(1))
                .andExpect(jsonPath("$.publicacionesPorDia", hasSize(30)))
                .andExpect(jsonPath("$.totalPerfiles").value(1))
                .andExpect(jsonPath("$.totalResenas").value(1))
                .andReturn();

        List<Map<String, Object>> dias = JsonPath.read(resultado.getResponse().getContentAsString(),
                "$.publicacionesPorDia");
        long creadas = dias.stream().mapToLong(dia -> ((Number) dia.get("total")).longValue()).sum();
        assertEquals(3, creadas);
        assertEquals(vendida, ((Number) JsonPath.read(resultado.getResponse().getContentAsString(),
                "$.topPublicaciones[2].id")).longValue());
    }

    @Test
    @DisplayName("Listado: incluye pendientes de pago, ordena lo más nuevo primero, filtra y pagina")
    void listaPublicacionesIncluyePendientesYFiltra() throws Exception {
        String marca = unico("listado");
        long pendiente = publicarPendiente("ana.lista", "Pendiente " + marca, "Otros");
        long pagada = publicarPendiente("beto.lista", "Pagada " + marca, "Otros");
        pagar("beto.lista", pagada);
        long ajena = publicarPendiente("ajeno.lista", "Ajena " + marca, "Libros");

        mvc.perform(con(get("/api/admin/publicaciones?texto=" + marca), admin(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.contenido", hasSize(3)))
                .andExpect(jsonPath("$.contenido[0].id").value(ajena))
                .andExpect(jsonPath("$.contenido[2].id").value(pendiente));
        mvc.perform(con(get("/api/admin/publicaciones?texto=" + marca + "&estadoPago=PENDIENTE"), admin(), null))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[*].id", org.hamcrest.Matchers.hasItem((int) pendiente)));
        mvc.perform(con(get("/api/admin/publicaciones?texto=" + marca + "&vendedor=ana.lista"), admin(), null))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].vendedor").value("ana.lista"));
        mvc.perform(con(get("/api/admin/publicaciones?texto=" + marca + "&categoria=Libros"), admin(), null))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(con(get("/api/admin/publicaciones?texto=" + marca + "&pagina=2&tamano=1"), admin(), null))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamano").value(1))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.contenido", hasSize(1)));
    }

    @Test
    @DisplayName("Eliminar: el ADMIN borra con el caso de uso existente; repetir da 404")
    void eliminaPublicacionComoAdmin() throws Exception {
        long id = publicarPendiente("ana.borrar", unico("Borrar "), "Otros");
        mvc.perform(con(delete("/api/admin/publicaciones/" + id), admin(), null))
                .andExpect(status().isNoContent());
        mvc.perform(con(get("/api/publicaciones/" + id), estudiante("ana.borrar"), null))
                .andExpect(status().isNotFound());
        mvc.perform(con(delete("/api/admin/publicaciones/" + id), admin(), null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Usuarios: lista perfiles con su conteo de publicaciones y sin teléfono; es solo lectura")
    void usuariosListaPerfilesConConteo() throws Exception {
        String carla = "carla.usuarios@correo.com";
        String diego = "diego.usuarios@correo.com";
        mvc.perform(con(get("/api/perfil"), estudiante(carla), null)).andExpect(status().isOk());
        mvc.perform(con(get("/api/perfil"), estudiante(diego), null)).andExpect(status().isOk());
        publicarPendiente(carla, unico("Carla uno "), "Otros");
        publicarPendiente(carla, unico("Carla dos "), "Libros");
        publicarPendiente(diego, unico("Diego uno "), "Otros");

        mvc.perform(con(get("/api/admin/usuarios"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[0].usuario").value(carla))
                .andExpect(jsonPath("$.contenido[0].correo").value(carla))
                .andExpect(jsonPath("$.contenido[0].nombre").value("carla.usuarios"))
                .andExpect(jsonPath("$.contenido[0].publicaciones").value(2))
                .andExpect(jsonPath("$.contenido[0].eliminada").value(false))
                .andExpect(jsonPath("$.contenido[0].telefono").doesNotExist())
                .andExpect(jsonPath("$.contenido[1].usuario").value(diego))
                .andExpect(jsonPath("$.contenido[1].publicaciones").value(1));
        mvc.perform(con(get("/api/admin/usuarios?pagina=2&tamano=1"), admin(), null))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario").value(diego));
    }
}
