package com.upmarket.catalogo.infrastructure.storage;

import com.cloudinary.Cloudinary;
import java.io.IOException;
import java.util.Map;

/** Implementación real del envoltorio: delega en el SDK oficial de Cloudinary. */
public class CloudinaryDocumentos implements ClienteDocumentosCloudinary {

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
}
