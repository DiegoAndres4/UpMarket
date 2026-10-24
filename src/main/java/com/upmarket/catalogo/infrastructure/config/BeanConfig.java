package com.upmarket.catalogo.infrastructure.config;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.out.*;
import com.upmarket.catalogo.application.service.*;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /** Al arrancar crea las categorías iniciales que falten (HU-9). Repetirlo no duplica nada. */
    @Bean
    ApplicationRunner categoriasIniciales(CategoriaUseCase categorias) {
        return args -> categorias.asegurarIniciales();
    }
}
