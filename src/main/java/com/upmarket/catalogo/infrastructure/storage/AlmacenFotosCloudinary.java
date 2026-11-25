package com.upmarket.catalogo.infrastructure.storage;

import com.cloudinary.Cloudinary;
import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.domain.model.Foto;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de Cloudinary: sube la imagen al CDN y devuelve su {@code secure_url} (https absoluta)
 * como URL de la foto. La firma y el secreto viven solo aquí, en el backend; el navegador nunca los ve.
 * Solo se activa cuando {@code upmarket.fotos.proveedor=cloudinary}.
 */
@Component
@ConditionalOnProperty(prefix = "upmarket.fotos", name = "proveedor", havingValue = "cloudinary")
public class AlmacenFotosCloudinary implements AlmacenFotosPort {

    private final Cloudinary cloudinary;

    public AlmacenFotosCloudinary(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public Foto guardar(byte[] contenido, String carpeta) {
        // El contenido se valida igual que en local: nada que no sea JPG/PNG de hasta 5 MB llega al CDN.
        String tipo = FotosValidadas.tipoDeContenido(contenido);
        Map<?, ?> resultado;
        try {
            resultado = cloudinary.uploader().upload(contenido, Map.of("folder", carpeta));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la foto a Cloudinary", e);
        }
        Object url = resultado.get("secure_url");
        if (url == null || url.toString().isBlank()) {
            throw new IllegalStateException("Cloudinary no devolvió la URL de la foto");
        }
        Object bytes = resultado.get("bytes");
        long tamano = bytes instanceof Number numero ? numero.longValue() : contenido.length;
        return new Foto(url.toString(), tipo, tamano);
    }

    @Override
    public void borrar(String url) {
        borrarConResultado(url);
    }

    @Override
    public boolean borrarConResultado(String url) {
        String idPublico = idPublico(url);
        if (idPublico == null) {
            return false;
        }
        try {
            Object resultado = cloudinary.uploader().destroy(idPublico, Map.of("invalidate", true)).get("result");
            return "ok".equals(resultado) || "not found".equals(resultado);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Deriva el public_id de una URL de Cloudinary; devuelve null si no parece una URL de Cloudinary. */
    static String idPublico(String url) {
        if (url == null) {
            return null;
        }
        String limpio = url.trim();
        int marca = limpio.indexOf("/upload/");
        if (!limpio.startsWith("http") || marca < 0) {
            return null;
        }
        String resto = limpio.substring(marca + "/upload/".length());
        resto = resto.replaceFirst("^v\\d+/", "");
        int punto = resto.lastIndexOf('.');
        if (punto > 0) {
            resto = resto.substring(0, punto);
        }
        return resto.isBlank() ? null : resto;
    }
}
