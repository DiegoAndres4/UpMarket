package com.upmarket.catalogo.infrastructure.config;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.out.*;
import com.upmarket.catalogo.application.service.*;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosCloudinary;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    @Bean
    CategoriaService categoriaService(CategoriaRepositoryPort categorias, PublicacionRepositoryPort publicaciones,
                                      TransaccionPort transaccion, PoliticaAcceso acceso) {
        return new CategoriaService(categorias, publicaciones, transaccion, acceso);
    }

    @Bean
    PublicacionService publicacionService(PublicacionRepositoryPort publicaciones, CategoriaRepositoryPort categorias,
                                          TransaccionPort transaccion, Clock reloj, PoliticaAcceso acceso) {
        return new PublicacionService(publicaciones, categorias, transaccion, reloj, acceso);
    }

    @Bean
    PagoService pagoService(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones,
                            PasarelaPagoPort pasarela, TransaccionPort transaccion, Clock reloj,
                            PoliticaAcceso acceso,
                            @Value("${upmarket.publicacion.tarifa:3000}") long tarifa) {
        return new PagoService(pagos, publicaciones, pasarela, transaccion, reloj, acceso, tarifa);
    }

    @Bean
    ResenaService resenaService(ResenaRepositoryPort resenas, TransaccionPort transaccion, Clock reloj,
                                PerfilPublicoPort perfiles) {
        return new ResenaService(resenas, transaccion, reloj, perfiles);
    }

    /** Al arrancar crea las categorías iniciales que falten (HU-9). Repetirlo no duplica nada. */
    @Bean
    ApplicationRunner categoriasIniciales(CategoriaUseCase categorias) {
        return args -> categorias.asegurarIniciales();
    }
}
