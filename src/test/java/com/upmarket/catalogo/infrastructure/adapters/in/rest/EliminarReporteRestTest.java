package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import com.upmarket.catalogo.domain.exception.AlmacenDocumentosException;
import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import com.upmarket.catalogo.infrastructure.adapters.out.persistence.DocumentoSpringJpaRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import com.upmarket.catalogo.infrastructure.storage.AlmacenDocumentosLocal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Eliminar un reporte: borra los dos archivos antes de las filas, es idempotente en el almacén,
 * conserva las filas si el almacén falla y no permite tocar recibos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "spring.datasource.url=jdbc:h2:mem:upmarket-eliminar-reporte;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "upmarket.documentos.directorio=target/documentos-test-eliminar"})
class EliminarReporteRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DocumentoSpringJpaRepository documentos;
    @Autowired
    private PerfilRepository perfiles;
    @MockitoBean(name = "almacenDocumentosPort")
    private AlmacenDocumentosPort almacen;
    @Autowired
    private AlmacenDocumentosLocal local;

    @BeforeEach
    void limpiar() {
        documentos.deleteAll();
        perfiles.deleteAll();
        when(almacen.guardar(any(byte[].class), anyString(), anyString(), anyString()))
                .thenAnswer(invocacion -> local.guardar(invocacion.getArgument(0), invocacion.getArgument(1),
                        invocacion.getArgument(2), invocacion.getArgument(3)));
        when(almacen.enlaceDescarga(any(), any())).thenReturn(null);
        when(almacen.leer(any())).thenAnswer(invocacion -> local.leer(invocacion.getArgument(0)));
    }

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario).claim("uid", 77).claim("roles", List.of(roles))
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private String admin() { return token("admin", "ADMIN", "USER"); }

    private String estudiante(String nombre) { return token(nombre, "USER"); }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    private String generarReporte() throws Exception {
        MvcResult resultado = mvc.perform(con(post("/api/admin/reportes/estadisticas"), admin()))
                .andExpect(status().isCreated()).andReturn();
        return resultado.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("El ADMIN borra el reporte completo: 204, dos archivos borrados y ya no aparece")
    void borraElReporteCompleto() throws Exception {
        String cuerpo = generarReporte();
        String numero = JsonPath.read(cuerpo, "$.numero");

        mvc.perform(con(delete("/api/admin/reportes/" + numero), admin()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DocumentoAlmacenado> borrados = ArgumentCaptor.forClass(DocumentoAlmacenado.class);
        verify(almacen, org.mockito.Mockito.times(2)).eliminar(borrados.capture());
        assertThat(borrados.getAllValues()).extracting(DocumentoAlmacenado::referencia).doesNotContainNull();
        assertThat(documentos.count()).isZero();
        mvc.perform(con(get("/api/admin/reportes"), admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(con(delete("/api/admin/reportes/" + numero), admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Borrar un reporte es solo del ADMIN")
    void soloElAdminBorra() throws Exception {
        String numero = JsonPath.read(generarReporte(), "$.numero");

        mvc.perform(con(delete("/api/admin/reportes/" + numero), estudiante("ana.borrar")))
                .andExpect(status().isForbidden());
        verify(almacen, org.mockito.Mockito.never()).eliminar(any());
        assertThat(documentos.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Si el almacén falla responde 502 y conserva las filas")
    void falloDelAlmacenConservaLasFilas() throws Exception {
        String numero = JsonPath.read(generarReporte(), "$.numero");
        doThrow(new AlmacenDocumentosException("El almacén no respondió")).when(almacen).eliminar(any());

        mvc.perform(con(delete("/api/admin/reportes/" + numero), admin()))
                .andExpect(status().isBadGateway());

        assertThat(documentos.count()).isEqualTo(2);
        mvc.perform(con(get("/api/admin/reportes"), admin()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("Un recibo no se puede eliminar por la ruta de reportes")
    void unReciboNoSeBorra() throws Exception {
        documentos.saveAndFlush(new com.upmarket.catalogo.infrastructure.adapters.out.persistence
                .DocumentoGeneradoJpaEntity(new DocumentoGenerado(null, TipoDocumento.RECIBO_PAGO,
                FormatoDocumento.PDF, "REC-000001", null, "local", "recibo.pdf", "recibo.pdf", 10L,
                Instant.now(), "admin")));

        mvc.perform(con(delete("/api/admin/reportes/REC-000001"), admin()))
                .andExpect(status().isNotFound());
        assertThat(documentos.count()).isEqualTo(1);
        verify(almacen, org.mockito.Mockito.never()).eliminar(any());
    }
}
