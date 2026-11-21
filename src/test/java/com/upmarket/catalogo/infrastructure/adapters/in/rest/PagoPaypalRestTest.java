package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort;
import com.upmarket.catalogo.application.port.out.PasarelaPaypalPort.ResultadoOrden;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** PayPal sandbox de punta a punta: configuración, orden, captura, validaciones y carrera de pagos. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguridad.jwt.secret-base64=Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==",
        "fotos.directorio=target/fotos-prueba",
        "pagos.proveedor=paypal",
        "pagos.paypal.client-id=cliente-publico",
        "pagos.paypal.client-secret=secreto-de-prueba",
        "pagos.paypal.monto-usd=0.75"})
class PagoPaypalRestTest {

    private static final String SECRETO = "Lu7e1SCfczRWZpilqgAQVZqNiJopaxOzxiu9UaLENKGTeGeBtIyfpBegmsIKQS3D6pmEiahNszQXjzxmhjabaw==";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PagoRepositoryPort pagos;

    @Autowired
    private PublicacionRepositoryPort publicaciones;

    @MockitoBean
    private PasarelaPaypalPort paypal;

    private String token(String usuario, String... roles) {
        Instant ahora = Instant.now();
        List<String> listaRoles = roles.length == 0 ? List.of("USER") : List.of(roles);
        return Jwts.builder().subject(usuario).claim("uid", 11).claim("roles", listaRoles)
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)), Jwts.SIG.HS384).compact();
    }

    private long publicarPendiente(String vendedor, String marca) throws Exception {
        String cuerpo = mvc.perform(post("/api/publicaciones")
                        .header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Bicicleta " + marca + "\",\"descripcion\":\"Descripción de prueba\","
                                + "\"precio\":\"300000\",\"estado\":\"usado\",\"categoria\":\"Otros\","
                                + "\"fotos\":[],\"whatsapp\":\"3001234567\",\"correo\":\"vendedor@correo.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private String persona() { return "paypal-" + UUID.randomUUID() + "@correo.com"; }

    @Test
    @DisplayName("La configuración pública solo expone el client id público, el monto USD y la moneda")
    void configuracionPublica() throws Exception {
        mvc.perform(get("/api/pagos/config").header("Authorization", "Bearer " + token(persona())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("paypal"))
                .andExpect(jsonPath("$.clientId").value("cliente-publico"))
                .andExpect(jsonPath("$.montoUsd").value(0.75))
                .andExpect(jsonPath("$.monedaPaypal").value("USD"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    @DisplayName("La orden de PayPal es solo del autor y devuelve el orderId")
    void ordenSoloDelAutor() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);

        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden")
                        .header("Authorization", "Bearer " + token("otro@correo.com")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden")
                        .header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-" + id));
    }

    @Test
    @DisplayName("Captura aprobada: guarda monto USD y proveedor PAYPAL, y activa la publicación")
    void capturaAprobada() throws Exception {
        String vendedor = persona();
        String marca = UUID.randomUUID().toString().replace("-", "");
        long id = publicarPendiente(vendedor, marca);
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.capturar("ORDER-" + id)).thenReturn(new PasarelaPaypalPort.ResultadoCaptura(
                "COMPLETED", "CAP-" + id, new BigDecimal("0.75"), "USD", String.valueOf(id)));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/captura").header("Authorization", autorizacion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"ORDER-" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.monto").value(3000.0));

        Pago pago = pagos.buscarPorPublicacion(id).orElseThrow();
        assertEquals(new BigDecimal("0.75"), pago.getMontoUsd());
        assertEquals(Pago.PROVEEDOR_PAYPAL, pago.getProveedor());
        assertTrue(publicaciones.buscarPorId(id).orElseThrow().estaPagada());
        mvc.perform(get("/api/publicaciones?texto=" + marca).header("Authorization", "Bearer " + token("ana@correo.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Monto, moneda o custom_id distintos: 409 y nada aprobado")
    void capturaConDatosQueNoCoinciden() throws Exception {
        record Caso(String estado, String moneda, String monto, String customId) { }
        List<Caso> casos = List.of(
                new Caso("COMPLETED", "USD", "1.00", null),
                new Caso("COMPLETED", "COP", "0.75", null),
                new Caso("COMPLETED", "USD", "0.75", "999999"),
                new Caso("PENDING", "USD", "0.75", null));

        for (Caso caso : casos) {
            String vendedor = persona();
            long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
            when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
            when(paypal.capturar(eq("ORDER-" + id))).thenReturn(new PasarelaPaypalPort.ResultadoCaptura(
                    caso.estado(), "CAP", new BigDecimal(caso.monto()), caso.moneda(),
                    caso.customId() == null ? String.valueOf(id) : caso.customId()));

            String autorizacion = "Bearer " + token(vendedor);
            mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/captura")
                            .header("Authorization", autorizacion)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"ORDER-" + id + "\"}"))
                    .andExpect(status().isConflict());

            assertTrue(pagos.buscarPorPublicacion(id).isEmpty());
            assertEquals(EstadoPago.PENDIENTE, publicaciones.buscarPorId(id).orElseThrow().getEstadoPago());
        }
    }

    @Test
    @DisplayName("Dos capturas simultáneas: solo una aprueba (bloqueo pesimista de la publicación)")
    void capturasSimultaneasSoloApruebanUna() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.capturar("ORDER-" + id)).thenReturn(new PasarelaPaypalPort.ResultadoCaptura(
                "COMPLETED", "CAP-" + id, new BigDecimal("0.75"), "USD", String.valueOf(id)));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> tarea = () -> {
                listos.countDown();
                largada.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/captura")
                                .header("Authorization", autorizacion)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderId\":\"ORDER-" + id + "\"}"))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(tarea);
            Future<Integer> b = pool.submit(tarea);
            listos.await(5, TimeUnit.SECONDS);
            largada.countDown();

            List<Integer> codigos = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).stream()
                    .sorted().toList();
            assertEquals(List.of(200, 409), codigos);

            List<Pago> aprobados = pagos.todos().stream()
                    .filter(pago -> pago.getPublicacionId() == id && pago.getEstado() == EstadoPago.APROBADO)
                    .toList();
            assertEquals(1, aprobados.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Una publicación ya pagada no crea una segunda orden de PayPal")
    void noSePuedeCrearOtraOrdenSiYaPago() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.capturar("ORDER-" + id)).thenReturn(new PasarelaPaypalPort.ResultadoCaptura(
                "COMPLETED", "CAP-" + id, new BigDecimal("0.75"), "USD", String.valueOf(id)));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/captura").header("Authorization", autorizacion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"ORDER-" + id + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isConflict());
        // El cobro simulado tampoco es una puerta trasera con proveedor=paypal.
        mvc.perform(post("/api/publicaciones/" + id + "/pago").header("Authorization", autorizacion))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Verificar una orden ya capturada aprueba una vez y expone tieneOrdenPaypal al dueño")
    void verificarOrdenCapturadaEsIdempotente() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.consultarOrden("ORDER-" + id)).thenReturn(new ResultadoOrden(
                "COMPLETED", "COMPLETED", "CAP-" + id, new BigDecimal("0.75"), "USD", String.valueOf(id)));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(get("/api/publicaciones/" + id).header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneOrdenPaypal").value(false));
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        mvc.perform(get("/api/publicaciones/" + id).header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneOrdenPaypal").value(true));

        String primero = mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/verificar")
                        .header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andReturn().getResponse().getContentAsString();
        String segundo = mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/verificar")
                        .header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(((Number) JsonPath.read(primero, "$.id")).longValue(),
                ((Number) JsonPath.read(segundo, "$.id")).longValue());
        assertEquals(1, pagos.todos().stream()
                .filter(pago -> pago.getPublicacionId() == id && pago.getEstado() == EstadoPago.APROBADO)
                .count());
        assertTrue(publicaciones.buscarPorId(id).orElseThrow().estaPagada());
    }

    @Test
    @DisplayName("Verificar una orden aprobada sin capturar la captura ahora")
    void verificarOrdenAprobadaCaptura() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.consultarOrden("ORDER-" + id))
                .thenReturn(new ResultadoOrden("APPROVED", null, null, null, null, null));
        when(paypal.capturar("ORDER-" + id)).thenReturn(new PasarelaPaypalPort.ResultadoCaptura(
                "COMPLETED", "CAP-" + id, new BigDecimal("0.75"), "USD", String.valueOf(id)));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/verificar").header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.referencia").value("CAP-" + id));
    }

    @Test
    @DisplayName("Verificar con custom_id de otra publicación: 409 y nada aprobado")
    void verificarCustomIdAjeno() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);
        when(paypal.consultarOrden("ORDER-" + id)).thenReturn(new ResultadoOrden(
                "COMPLETED", "COMPLETED", "CAP", new BigDecimal("0.75"), "USD", "999999"));

        String autorizacion = "Bearer " + token(vendedor);
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/verificar").header("Authorization", autorizacion))
                .andExpect(status().isConflict());

        assertTrue(pagos.buscarPorPublicacion(id).isEmpty());
        assertTrue(publicaciones.buscarPorId(id).orElseThrow().getEstadoPago() == EstadoPago.PENDIENTE);
    }

    @Test
    @DisplayName("Verificar es solo del autor: otra persona recibe 403")
    void verificarSoloDelAutor() throws Exception {
        String vendedor = persona();
        long id = publicarPendiente(vendedor, UUID.randomUUID().toString());
        when(paypal.crearOrden(anyLong(), anyString(), any(BigDecimal.class))).thenReturn("ORDER-" + id);

        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/orden")
                .header("Authorization", "Bearer " + token(vendedor))).andExpect(status().isOk());
        mvc.perform(post("/api/publicaciones/" + id + "/pago/paypal/verificar")
                        .header("Authorization", "Bearer " + token("intruso@correo.com")))
                .andExpect(status().isForbidden());
    }
}
