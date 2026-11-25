package com.upmarket.catalogo.infrastructure.config;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.admin.AdminUseCase;
import com.upmarket.catalogo.application.port.in.admin.DocumentosUseCase;
import com.upmarket.catalogo.application.port.out.*;
import com.upmarket.catalogo.application.service.*;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.infrastructure.adapters.out.payment.ClientePaypal;
import com.upmarket.catalogo.infrastructure.adapters.out.payment.ClientePaypalHttp;
import com.upmarket.catalogo.infrastructure.adapters.out.payment.PasarelaPaypal;
import com.upmarket.catalogo.infrastructure.storage.AlmacenDocumentosCloudinary;
import com.upmarket.catalogo.infrastructure.storage.AlmacenDocumentosLocal;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosCloudinary;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Raíz de composición. El dominio y la aplicación no llevan anotaciones de Spring (DIP):
 * es esta clase, en el borde, la que los construye y les entrega sus dependencias.
 */
@Configuration
public class BeanConfig {

    @Bean
    Clock reloj(@Value("${negocio.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    @Bean
    PoliticaAcceso politicaAcceso() {
        return new PoliticaAcceso();
    }

    /**
     * Elige el proveedor de fotos con {@code upmarket.fotos.proveedor} (local por defecto, para que
     * dev/tests/CI funcionen sin credenciales). Es {@code @Primary} porque el componente local también
     * implementa el puerto.
     */
    @Bean
    @Primary
    AlmacenFotosPort almacenFotosPort(@Value("${upmarket.fotos.proveedor:local}") String proveedor,
                                      AlmacenFotosLocal local,
                                      ObjectProvider<AlmacenFotosCloudinary> cloudinary) {
        String elegido = proveedor == null ? "local" : proveedor.trim();
        if (elegido.isEmpty() || "local".equals(elegido)) {
            return local;
        }
        if ("cloudinary".equals(elegido)) {
            AlmacenFotosCloudinary adaptador = cloudinary.getIfAvailable();
            if (adaptador == null) {
                throw new IllegalStateException(
                        "upmarket.fotos.proveedor=cloudinary requiere credenciales de Cloudinary");
            }
            return adaptador;
        }
        throw new IllegalStateException("Proveedor de fotos desconocido: '" + proveedor
                + "'. Valores válidos: local, cloudinary.");
    }

    /**
     * Mismo interruptor que las fotos: con {@code upmarket.fotos.proveedor} se elige dónde viven
     * también los documentos. {@code @Primary} porque el componente local siempre existe.
     */
    @Bean
    @Primary
    AlmacenDocumentosPort almacenDocumentosPort(@Value("${upmarket.fotos.proveedor:local}") String proveedor,
                                                AlmacenDocumentosLocal local,
                                                ObjectProvider<AlmacenDocumentosCloudinary> cloudinary) {
        String elegido = proveedor == null ? "local" : proveedor.trim();
        if (elegido.isEmpty() || "local".equals(elegido)) {
            return local;
        }
        if ("cloudinary".equals(elegido)) {
            AlmacenDocumentosCloudinary adaptador = cloudinary.getIfAvailable();
            if (adaptador == null) {
                throw new IllegalStateException(
                        "upmarket.fotos.proveedor=cloudinary requiere credenciales de Cloudinary");
            }
            return adaptador;
        }
        throw new IllegalStateException("Proveedor de almacenamiento desconocido: '" + proveedor
                + "'. Valores válidos: local, cloudinary.");
    }

    @Bean
    DocumentosUseCase documentosUseCase(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones,
                                        PerfilAdminPort perfiles, DocumentoRepositoryPort documentos,
                                        AlmacenDocumentosPort almacen, GeneradorDocumentosPort generador,
                                        AdminUseCase admin, PoliticaAcceso acceso, Clock reloj) {
        return new DocumentosService(pagos, publicaciones, perfiles, documentos, almacen, generador, admin, acceso,
                reloj);
    }

    @Bean
    CategoriaService categoriaService(CategoriaRepositoryPort categorias, PublicacionRepositoryPort publicaciones,
                                      TransaccionPort transaccion, PoliticaAcceso acceso) {
        return new CategoriaService(categorias, publicaciones, transaccion, acceso);
    }

    @Bean
    PublicacionService publicacionService(PublicacionRepositoryPort publicaciones, CategoriaRepositoryPort categorias,
                                          TransaccionPort transaccion, Clock reloj, PoliticaAcceso acceso,
                                          GestionFotosPort gestionFotos, InteresRepositoryPort interesados,
                                          PerfilContactoPort perfilesContacto) {
        return new PublicacionService(publicaciones, categorias, transaccion, reloj, acceso, gestionFotos,
                interesados, perfilesContacto);
    }

    @Bean
    PagoService pagoService(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones,
                            PasarelaPagoPort pasarela, PasarelaPaypalPort paypal, TransaccionPort transaccion,
                            Clock reloj, PoliticaAcceso acceso,
                            @Value("${upmarket.publicacion.tarifa:3000}") long tarifa,
                            @Value("${pagos.proveedor:simulado}") String proveedor,
                            @Value("${pagos.paypal.client-id:}") String paypalClientId,
                            @Value("${pagos.paypal.monto-usd:0.75}") BigDecimal montoUsd,
                            @Value("${pagos.paypal.base-url:https://api-m.sandbox.paypal.com}") String paypalBaseUrl) {
        boolean sandbox = paypalBaseUrl != null && paypalBaseUrl.toLowerCase().contains("sandbox");
        return new PagoService(pagos, publicaciones, pasarela, paypal, transaccion, reloj, acceso, tarifa,
                proveedor, paypalClientId, montoUsd, sandbox);
    }

    /**
     * Cliente de PayPal. Si el proveedor elegido es paypal, las credenciales son obligatorias: la
     * aplicación falla al arrancar en vez de aceptar pagos que no puede cobrar. Con simulado no se
     * hace ninguna llamada de red.
     */
    @Bean
    ClientePaypal clientePaypal(RestClient.Builder builder,
                                @Value("${pagos.proveedor:simulado}") String proveedor,
                                @Value("${pagos.paypal.client-id:}") String clientId,
                                @Value("${pagos.paypal.client-secret:}") String clientSecret,
                                @Value("${pagos.paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {
        boolean paypal = "paypal".equalsIgnoreCase(proveedor == null ? "" : proveedor.trim());
        if (paypal && (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())) {
            throw new IllegalStateException(
                    "PAGOS_PROVEEDOR=paypal requiere PAYPAL_CLIENT_ID y PAYPAL_CLIENT_SECRET");
        }
        return new ClientePaypalHttp(builder, baseUrl, clientId, clientSecret);
    }

    @Bean
    PasarelaPaypalPort pasarelaPaypal(ClientePaypal clientePaypal) {
        return new PasarelaPaypal(clientePaypal);
    }

    @Bean
    ResenaService resenaService(ResenaRepositoryPort resenas, TransaccionPort transaccion, Clock reloj,
                                PerfilPublicoPort perfiles) {
        return new ResenaService(resenas, transaccion, reloj, perfiles);
    }

    @Bean
    AdminUseCase adminUseCase(PublicacionRepositoryPort publicaciones, PagoRepositoryPort pagos,
                              ResenaRepositoryPort resenas, PerfilAdminPort perfiles,
                              PublicacionUseCase casosDePublicacion, PoliticaAcceso acceso, Clock reloj) {
        return new AdminService(publicaciones, pagos, resenas, perfiles, casosDePublicacion, acceso, reloj);
    }

    /** Al arrancar crea las categorías iniciales que falten (HU-9). Repetirlo no duplica nada. */
    @Bean
    ApplicationRunner categoriasIniciales(CategoriaUseCase categorias) {
        return args -> categorias.asegurarIniciales();
    }
}
