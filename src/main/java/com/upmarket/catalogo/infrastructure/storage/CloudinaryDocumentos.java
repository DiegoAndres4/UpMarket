package com.upmarket.catalogo.infrastructure.storage;

import com.cloudinary.Cloudinary;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Implementación real del envoltorio: delega en el SDK oficial de Cloudinary. */
public class CloudinaryDocumentos implements ClienteDocumentosCloudinary {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Cloudinary cloudinary;

    public CloudinaryDocumentos(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public Map<?, ?> subir(byte[] contenido, Map<String, Object> opciones) throws IOException {
        return cloudinary.uploader().upload(contenido, opciones);
    }

    @Override
    public String enlacePrivado(String publicId, String formato, Map<String, Object> opciones) throws Exception {
        return cloudinary.privateDownload(publicId, formato, opciones);
    }

    @Override
    public byte[] descargar(String enlace) throws IOException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(enlace)).GET().build();
        try {
            HttpResponse<byte[]> respuesta = HTTP.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
            if (respuesta.statusCode() / 100 != 2) {
                throw new IOException("Cloudinary respondió " + respuesta.statusCode() + " al leer el documento");
            }
            return respuesta.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la lectura del documento en Cloudinary", e);
        }
    }
}
