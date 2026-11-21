package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.application.port.out.DocumentoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.infrastructure.perfil.CredencialesPort;
import com.upmarket.catalogo.infrastructure.perfil.PerfilEliminado;
import com.upmarket.catalogo.infrastructure.perfil.PerfilEliminadoRepository;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Al eliminar la cuenta el registro no se pierde: perfil archivado y publicaciones archivadas. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "fotos.directorio=target/fotos-prueba"})
class ArchivadoCuentaRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PerfilRepository perfiles;

    @Autowired
    private PerfilEliminadoRepository eliminados;

    @Autowired
    private PublicacionRepositoryPort publicaciones;

    @Autowired
    private PagoRepositoryPort pagos;

    @Autowired
    private DocumentoRepositoryPort documentos;

    @MockitoBean
    private CredencialesPort credenciales;

    private String token(String usuario, Instant emitidoEn, String... roles) {
        List<String> listaRoles = roles.length == 0 ? List.of("USER") : List.of(roles);
        return Jwts.builder().subject(usuario).claim("uid", 7).claim("roles", listaRoles)
                .issuedAt(Date.from(emitidoEn)).expiration(Date.from(emitidoEn.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private long publicarPendiente(String vendedor, String titulo, String marca) throws Exception {
        String cuerpo = mvc.perform(post("/api/publicaciones")
                        .header("Authorization", "Bearer " + token(vendedor, Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + titulo + "\",\"descripcion\":\"Descripción de prueba\","
                                + "\"precio\":\"300000\",\"estado\":\"usado\",\"categoria\":\"Otros\","
                                + "\"fotos\":[{\"url\":\"https://archivos.example/a.jpg\",\"tipo\":\"image/jpeg\","
                                + "\"tamanoBytes\":200000}],\"whatsapp\":\"3001234567\","
                                + "\"correo\":\"vendedor@correo.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    @Test
    @DisplayName("Eliminar la cuenta archiva el perfil, archiva sus publicaciones y conserva pagos y recibos")
    void eliminarCuentaArchivaSinPerderRegistro() throws Exception {
        String usuario = "archivo-" + UUID.randomUUID() + "@correo.com";
        String marca = "marca" + UUID.randomUUID().toString().replace("-", "");
        String autorizacion = "Bearer " + token(usuario, Instant.now());

        mvc.perform(put("/api/perfil").header("Authorization", autorizacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Archivo\",\"telefono\":\"300 123 4567\","
                                + "\"fotoUrl\":\"https://archivos.example/perfil.jpg\"}"))
                .andExpect(status().isOk());

        long id = publicarPendiente(usuario, "Bicicleta " + marca, marca);
        mvc.perform(post("/api/publicaciones/" + id + "/pago").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        Pago pago = pagos.buscarPorPublicacion(id).orElseThrow();
        mvc.perform(get("/api/publicaciones/" + id + "/recibo").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        assertTrue(documentos.buscarRecibo(pago.getId()).isPresent());

        mvc.perform(delete("/api/perfil").header("Authorization", autorizacion))
                .andExpect(status().isNoContent());
        verify(credenciales).eliminarCuenta(autorizacion);

        // El usuario queda libre: la fila activa desaparece y el perfil completo se conserva aparte.
        assertTrue(perfiles.findById(usuario).isEmpty());
        PerfilEliminado archivo = eliminados.findFirstByUsuarioOrderByEliminadoEnDesc(usuario).orElseThrow();
        assertEquals(usuario, archivo.getUsuario());
        assertEquals("Ana Archivo", archivo.getNombre());
        assertEquals("3001234567", archivo.getTelefono());
        assertEquals(usuario, archivo.getCorreo());
        assertEquals("https://archivos.example/perfil.jpg", archivo.getFotoUrl());
        assertEquals(PerfilEliminado.MOTIVO_BAJA_USUARIO, archivo.getMotivo());
        assertNotNull(archivo.getEliminadoEn());

        // La publicación no se borra: queda archivada y su pago y recibo siguen existiendo.
        Publicacion archivada = publicaciones.buscarPorId(id).orElseThrow();
        assertTrue(archivada.estaArchivada());
        assertEquals(pago.getId(), pagos.buscarPorPublicacion(id).orElseThrow().getId());
        assertTrue(documentos.buscarRecibo(pago.getId()).isPresent());

        // Invisible en búsquedas y en el detalle, incluso para el ADMIN.
        String admin = "Bearer " + token("admin", Instant.now(), "ADMIN", "USER");
        mvc.perform(get("/api/publicaciones?texto=" + marca).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/publicaciones/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
        // El recibo histórico sigue disponible para el ADMIN.
        mvc.perform(get("/api/publicaciones/" + id + "/recibo").header("Authorization", admin))
                .andExpect(status().isOk());

        // Cuenta nueva con el mismo usuario: perfil nuevo, sin publicaciones archivadas y sin poder pagarlas.
        Instant emitidoDespues = archivo.getEliminadoEn().plusSeconds(2);
        String nuevaCuenta = "Bearer " + token(usuario, emitidoDespues);
        mvc.perform(get("/api/perfil").header("Authorization", nuevaCuenta)).andExpect(status().isOk());
        assertTrue(perfiles.findById(usuario).isPresent());
        mvc.perform(get("/api/publicaciones/mias").header("Authorization", nuevaCuenta))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/publicaciones/" + id + "/pago").header("Authorization", nuevaCuenta))
                .andExpect(status().isNotFound());

        // El token de la cuenta eliminada (emitido antes de la baja) deja de servir.
        mvc.perform(get("/api/perfil").header("Authorization", autorizacion))
                .andExpect(status().isUnauthorized());
    }
}
