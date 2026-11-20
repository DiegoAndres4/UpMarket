package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.DocumentoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PagoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.PublicacionSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.ResenaSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reporte de estadísticas: PDF y XML generados desde una sola foto, XML contra su XSD y
 * números idénticos a {@code GET /api/admin/estadisticas}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "spring.datasource.url=jdbc:h2:mem:upmarket-reporte-rest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "upmarket.documentos.directorio=target/documentos-test-reporte"})
class ReporteRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";
    private static final String NS = "https://upmarket.local/schema/estadisticas/1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DocumentoSpringJpaRepository documentos;
    @Autowired
    private PagoSpringJpaRepository pagos;
    @Autowired
    private ResenaSpringJpaRepository resenas;
    @Autowired
    private PublicacionSpringJpaRepository publicaciones;
    @Autowired
    private PerfilRepository perfiles;

    private long pendiente;
    private long pagada;

    @BeforeEach
    void prepararDatos() throws Exception {
        documentos.deleteAll();
        pagos.deleteAll();
        resenas.deleteAll();
        publicaciones.deleteAll();
        perfiles.deleteAll();

        String vendedor = "vendedor.reportes@correo.com";
        String comprador = "comprador.reportes";
        pendiente = publicarPendiente(vendedor, "Pendiente de reporte", "Otros");
        pagada = publicarPendiente(vendedor, "Título & <b>negrita</b> de reporte", "Otros");
        long vendida = publicarPendiente(vendedor, "Vendida de reporte", "Libros");
        pagar(vendedor, pagada);
        pagar(vendedor, vendida);
        mvc.perform(con(post("/api/publicaciones/" + vendida + "/sold"), estudiante(vendedor), null))
                .andExpect(status().isOk());
        mvc.perform(con(get("/api/publicaciones/" + pagada), estudiante(comprador), null))
                .andExpect(status().isOk());
        mvc.perform(con(post("/api/perfil/" + vendedor + "/resenas"), estudiante(comprador),
                "{\"estrellas\":5,\"comentario\":\"Muy buen vendedor\"}")).andExpect(status().isCreated());
        mvc.perform(con(get("/api/perfil"), estudiante(vendedor), null)).andExpect(status().isOk());
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

    private String json(String titulo, String categoria) {
        return "{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\",\"precio\":\"50000\","
                + "\"estado\":\"usado\",\"categoria\":\"" + categoria + "\","
                + "\"fotos\":[{\"url\":\"https://archivos.example/a.jpg\",\"tipo\":\"image/jpeg\",\"tamanoBytes\":200000}],"
                + "\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}";
    }

    private long publicarPendiente(String quien, String titulo, String categoria) throws Exception {
        String cuerpo = mvc.perform(con(post("/api/publicaciones"), estudiante(quien), json(titulo, categoria)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private void pagar(String quien, long id) throws Exception {
        mvc.perform(con(post("/api/publicaciones/" + id + "/pago"), estudiante(quien), null))
                .andExpect(status().isOk());
    }

    private String generarReporte() throws Exception {
        MvcResult resultado = mvc.perform(con(post("/api/admin/reportes/estadisticas"), admin(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.matchesPattern("EST-\\d{6}")))
                .andExpect(jsonPath("$.pdf.formato").value("PDF"))
                .andExpect(jsonPath("$.xml.formato").value("XML"))
                .andExpect(jsonPath("$.generadoPor").value("admin"))
                .andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(cuerpo, "$.pdf.numero"))
                .isEqualTo(JsonPath.<String>read(cuerpo, "$.xml.numero"));
        return cuerpo;
    }

    private byte[] descargar(long id) throws Exception {
        return mvc.perform(con(get("/api/admin/documentos/" + id + "/descarga"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static Element primero(Document documento, String nombre) {
        NodeList nodos = documento.getElementsByTagNameNS(NS, nombre);
        assertThat(nodos.getLength()).as("elemento " + nombre).isGreaterThan(0);
        return (Element) nodos.item(0);
    }

    @Test
    @DisplayName("Solo el ADMIN genera y consulta reportes")
    void soloElAdminEntra() throws Exception {
        mvc.perform(post("/api/admin/reportes/estadisticas")).andExpect(status().isUnauthorized());
        String usuario = estudiante("ana.reportes");
        mvc.perform(con(post("/api/admin/reportes/estadisticas"), usuario, null))
                .andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/reportes"), usuario, null)).andExpect(status().isForbidden());
        mvc.perform(con(get("/api/admin/reportes"), admin(), null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Genera PDF y XML con el mismo número y los mismos totales de las estadísticas")
    void generaAmbosDesdeUnaSolaFoto() throws Exception {
        String cuerpo = generarReporte();
        String numero = JsonPath.read(cuerpo, "$.numero");
        long pdfId = ((Number) JsonPath.read(cuerpo, "$.pdf.id")).longValue();
        long xmlId = ((Number) JsonPath.read(cuerpo, "$.xml.id")).longValue();

        String estadisticas = mvc.perform(con(get("/api/admin/estadisticas"), admin(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Number) JsonPath.read(estadisticas, "$.totalPublicaciones")).isEqualTo(3);
        assertThat((Number) JsonPath.read(estadisticas, "$.pagosAprobados")).isEqualTo(2);
        assertThat(((Number) JsonPath.read(estadisticas, "$.ingresosAprobados")).doubleValue()).isEqualTo(6000.0);

        byte[] xml = descargar(xmlId);
        String textoXml = new String(xml, StandardCharsets.UTF_8);

        // Valida contra el XSD propio.
        SchemaFactory fabrica = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema esquema = fabrica.newSchema(getClass().getResource("/xsd/estadisticas-1.xsd"));
        esquema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));

        DocumentBuilderFactory constructores = DocumentBuilderFactory.newInstance();
        constructores.setNamespaceAware(true);
        Document documento = constructores.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        Element raiz = documento.getDocumentElement();
        assertThat(raiz.getLocalName()).isEqualTo("reporteEstadisticas");
        assertThat(raiz.getNamespaceURI()).isEqualTo(NS);
        assertThat(raiz.getAttribute("numero")).isEqualTo(numero);
        assertThat(raiz.getAttribute("version")).isEqualTo("1.0");
        assertThat(raiz.getAttribute("zonaHoraria")).isEqualTo("America/Bogota");
        assertThat(raiz.getAttribute("generadoEn")).startsWith("20");

        assertThat(primero(documento, "publicaciones").getAttribute("total")).isEqualTo("3");
        assertThat(primero(documento, "pagos").getAttribute("aprobados")).isEqualTo("2");
        assertThat(primero(documento, "pagos").getAttribute("pendientes")).isEqualTo("1");
        assertThat(primero(documento, "pagos").getAttribute("rechazados")).isEqualTo("0");
        assertThat(primero(documento, "ingresosSimulados").getTextContent()).isEqualTo("6000.00");
        assertThat(primero(documento, "ingresosSimulados").getAttribute("moneda")).isEqualTo("COP");
        assertThat(primero(documento, "vistas").getAttribute("total")).isEqualTo("1");
        assertThat(primero(documento, "perfiles").getAttribute("total")).isEqualTo("1");
        assertThat(primero(documento, "resenas").getAttribute("total")).isEqualTo("1");
        assertThat(documento.getElementsByTagNameNS(NS, "dia").getLength()).isEqualTo(30);
        assertThat(documento.getElementsByTagNameNS(NS, "nota").getLength()).isGreaterThanOrEqualTo(2);
        assertThat(textoXml).contains("Las publicaciones de demostración se activaron sin registro de pago.");

        Element top1 = (Element) documento.getElementsByTagNameNS(NS, "publicacion").item(0);
        assertThat(top1.getAttribute("id")).isEqualTo(Long.toString(pagada));
        assertThat(top1.getAttribute("titulo")).contains("<b>negrita</b>"); // el XML escapa, el texto queda literal

        byte[] pdf = descargar(pdfId);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument documentoPdf = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documentoPdf);
            assertThat(texto).contains(numero).contains("Reporte de").contains("Generado por")
                    .contains("6.000").contains("<b>negrita</b>");
        }
    }

    @Test
    @DisplayName("El historial agrupa PDF y XML por número y ordena lo más nuevo primero")
    void historialAgrupaPorNumero() throws Exception {
        String primero = generarReporte();
        String segundo = generarReporte();
        String numeroPrimero = JsonPath.read(primero, "$.numero");
        String numeroSegundo = JsonPath.read(segundo, "$.numero");
        assertThat(numeroSegundo).isNotEqualTo(numeroPrimero);

        mvc.perform(con(get("/api/admin/reportes?pagina=1&tamano=10"), admin(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[0].numero").value(numeroSegundo))
                .andExpect(jsonPath("$.contenido[0].pdf.id").isNumber())
                .andExpect(jsonPath("$.contenido[0].xml.id").isNumber())
                .andExpect(jsonPath("$.contenido[0].generadoPor").value("admin"))
                .andExpect(jsonPath("$.contenido[1].numero").value(numeroPrimero));
    }

    @Test
    @DisplayName("El reporte incluye la publicación pendiente en el total")
    void cuentaPendientesDePago() throws Exception {
        String cuerpo = generarReporte();
        long xmlId = ((Number) JsonPath.read(cuerpo, "$.xml.id")).longValue();
        String texto = new String(descargar(xmlId), StandardCharsets.UTF_8);
        assertThat(texto).contains("cantidad=\"1\"").contains("PENDIENTE");
        assertThat(pendiente).isPositive();
    }
}
