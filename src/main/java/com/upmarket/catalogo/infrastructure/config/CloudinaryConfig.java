package com.upmarket.catalogo.infrastructure.config;

import com.cloudinary.Cloudinary;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Construye el cliente de Cloudinary SOLO cuando {@code upmarket.fotos.proveedor=cloudinary}.
 * Las credenciales llegan por variables de entorno; si faltan, la aplicación falla al arrancar
 * con un mensaje claro en lugar de arrancar con un proveedor a medias.
 */
@Configuration
@ConditionalOnProperty(prefix = "upmarket.fotos", name = "proveedor", havingValue = "cloudinary")
public class CloudinaryConfig {

    @Bean
    Cloudinary cloudinary(@Value("${cloudinary.url:}") String url,
                          @Value("${cloudinary.cloud-name:}") String nombreNube,
                          @Value("${cloudinary.api-key:}") String clave,
                          @Value("${cloudinary.api-secret:}") String secreto) {
        if (!vacio(url)) {
            return new Cloudinary(url.trim());
        }
        if (vacio(nombreNube) || vacio(clave) || vacio(secreto)) {
            throw new IllegalStateException(
                    "upmarket.fotos.proveedor=cloudinary pero faltan credenciales. "
                            + "Defina CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET, "
                            + "o bien CLOUDINARY_URL (cloudinary://api_key:api_secret@cloud_name).");
        }
        Map<String, String> configuracion = new LinkedHashMap<>();
        configuracion.put("cloud_name", nombreNube.trim());
        configuracion.put("api_key", clave.trim());
        configuracion.put("api_secret", secreto.trim());
        return new Cloudinary(configuracion);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
