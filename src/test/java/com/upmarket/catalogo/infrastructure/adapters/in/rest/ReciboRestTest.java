package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.DocumentoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PagoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PublicacionSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.ResenaSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * Recibo de pago por HTTP: solo ADMIN, solo pagos aprobados, idempotente y con PDF real.
 * El almacenamiento local escribe en target/ para no ensuciar el repositorio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "spring.datasource.url=jdbc:h2:mem:upmarket-recibo-rest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "upmarket.documentos.directorio=target/documentos-test-recibo"})
class ReciboRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DocumentoSpringJpaRepository documentos;
    @Autowired
    private PagoSpringJpaRepository pagosJpa;
    @Autowired
    private PagoRepositoryPort pagos;
    @Autowired
    private ResenaSpringJpaRepository resenas;
    @Autowired
    private PublicacionSpringJpaRepository publicaciones;
    @Autowired
    private PerfilRepository perfiles;

    @BeforeEach
    void limpiar() {
        documentos.deleteAll();
        pagosJpa.deleteAll();
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

    private String json(String titulo) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\",\"precio\":\"50000\","
                + "\"estado\":\"usado\",\"categoria\":\"Otros\","
                + "\"fotos\":[{\"url\":\"https://archivos.example/a.jpg\",\"tipo\":\"image/jpeg\",\"tamanoBytes\":200000}],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}";
    }

    private long publicarPendiente(String quien, String titulo) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), estudiante(quien), json(titulo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long pagar(String quien, long publicacionId) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones/" + publicacionId + "/pago"),
                        estudiante(quien), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private MvcResult recibo(String token, long pagoId) throws Exception {
        return mvc.perform(con(post("/api/admin/pagos/" + pagoId + "/recibo"), token, null)).andReturn();
    }

    @Test
    @DisplayName("Solo el ADMIN lista pagos, genera recibos y descarga documentos")
    void soloElAdminEntra() throws Exception {
        mvc.perform(get("/api/admin/pagos")).andExpect(status().isUnauthorized());
        String usuario = estudiante("ana.recibo");
        mvc.perform(con(get("/api/admin/pagos"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(post("/api/admin/pagos/1/recibo"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/documentos/1/descarga"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/documentos/1/contenido"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/pagos"), admin(), null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Genera el recibo una sola vez: 201 la primera vez, 200 la segunda con el mismo documento")
    void generaElReciboUnaSolaVez() throws Exception {
        long publicacion = publicarPendiente("maria.recibo", "Calculadora de prueba");
        long pago = pagar("maria.recibo", publicacion);

        MvcResult primera = recibo(admin(), pago);
        assertThat(primera.getResponse().getStatus()).isEqualTo(201);
        String primero = primera.getResponse().getContentAsString();
        MvcResult segunda = recibo(admin(), pago);
        assertThat(segunda.getResponse().getStatus()).isEqualTo(200);
        String segundo = segunda.getResponse().getContentAsString();

        long idPrimero = ((Number) JsonPath.read(primero, "$.id")).longValue();
        long idSegundo = ((Number) JsonPath.read(segundo, "$.id")).longValue();
        assertThat(idSegundo).isEqualTo(idPrimero);
        assertThat((String) JsonPath.read(segundo, "$.numero")).matches("REC-\\d{6}");
        assertThat(documentos.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un pago no aprobado no tiene recibo (409)")
    void pagoPendienteNoGeneraRecibo() throws Exception {
        long publicacion = publicarPendiente("pedro.recibo", "Artículo sin pagar");
        Pago pendiente = pagos.guardar(Pago.reconstruir(null, publicacion, BigDecimal.valueOf(3000),
                com.upmarket.catalogo.domain.model.EstadoPago.PENDIENTE, "SIM-PENDIENTE", Instant.now(), null));

        mvc.perform(con(post("/api/admin/pagos/" + pendiente.getId() + "/recibo"), admin(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("aprobados")));
    }

    @Test
    @DisplayName("El PDF existe, empieza con %PDF y muestra el número y el monto")
    void elPdfTieneNumeroYMonto() throws Exception {
        long publicacion = publicarPendiente("luis.recibo", "Bicicleta de prueba");
        long pago = pagar("luis.recibo", publicacion);
        String cuerpo = recibo(admin(), pago).getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        String numero = JsonPath.read(cuerpo, "$.numero");

        MvcResult descarga = mvc.perform(con(get("/api/admin/documentos/" + id + "/descarga"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn();
        byte[] pdf = descarga.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains(numero).contains("3.000").contains("Pagado");
        }
    }

    @Test
    @DisplayName("Escapa el HTML del título: un <script> se dibuja como texto, no como etiqueta")
    void escapaElTituloDelUsuario() throws Exception {
        long publicacion = publicarPendiente("eva.recibo", "Oferta <script>alert(1)</script>");
        long pago = pagar("eva.recibo", publicacion);
        String cuerpo = recibo(admin(), pago).getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();

        byte[] pdf = mvc.perform(con(get("/api/admin/documentos/" + id + "/descarga"), admin(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("alert(1)").contains("<script>");
        }
    }

    @Test
    @DisplayName("Ver en línea: PDF inline sin caché para el ADMIN; un id desconocido da 404")
    void verReciboEnLinea() throws Exception {
        long publicacion = publicarPendiente("nora.recibo", "Lámpara de prueba");
        long pago = pagar("nora.recibo", publicacion);
        String cuerpo = recibo(admin(), pago).getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();

        MvcResult vista = mvc.perform(con(get("/api/admin/documentos/" + id + "/contenido"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(new String(vista.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");

        mvc.perform(con(get("/api/admin/documentos/999999/contenido"), admin(), null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El listado de pagos trae publicación, comprador y el recibo ya generado")
    void listadoDePagosConRecibo() throws Exception {
        long publicacion = publicarPendiente("carla.recibo", "Mesa de prueba");
        long pago = pagar("carla.recibo", publicacion);
        recibo(admin(), pago);

        mvc.perform(con(get("/api/admin/pagos?pagina=1&tamano=10"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(pago))
                .andExpect(jsonPath("$.contenido[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$.contenido[0].monto").value(3000.0))
                .andExpect(jsonPath("$.contenido[0].publicacion.titulo").value("Mesa de prueba"))
                .andExpect(jsonPath("$.contenido[0].comprador.usuario").value("carla.recibo"))
                .andExpect(jsonPath("$.contenido[0].recibo.existe").value(true))
                .andExpect(jsonPath("$.contenido[0].recibo.numero").value(org.hamcrest.Matchers.startsWith("REC-")));
    }
}
