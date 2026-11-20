package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import com.upmarket.catalogo.domain.exception.AlmacenDocumentosException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Documentos en Cloudinary, siempre PRIVADOS: {@code resource_type=raw} y {@code type=private}, de
 * modo que el archivo no queda accesible por una URL pública. La descarga se firma por petición con
 * caducidad. Solo se activa con {@code upmarket.fotos.proveedor=cloudinary} (mismo interruptor que
 * las fotos: un solo cambio mueve todo el almacenamiento binario).
 */
@Component
@ConditionalOnProperty(prefix = "upmarket.fotos", name = "proveedor", havingValue = "cloudinary")
public class AlmacenDocumentosCloudinary implements AlmacenDocumentosPort {

    private final ClienteDocumentosCloudinary cliente;

    public AlmacenDocumentosCloudinary(ClienteDocumentosCloudinary cliente) {
        this.cliente = cliente;
    }

    @Override
    public DocumentoAlmacenado guardar(byte[] contenido, String nombreArchivo, String carpeta, String contentType) {
        Map<String, Object> opciones = new LinkedHashMap<>();
        opciones.put("resource_type", "raw");
        opciones.put("type", "private");
        opciones.put("folder", carpeta);
        opciones.put("use_filename", true);
        opciones.put("unique_filename", true);
        opciones.put("filename_override", nombreArchivo);

        Map<?, ?> resultado;
        try {
            resultado = cliente.subir(contenido, opciones);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir el documento a Cloudinary", e);
        }
        Object publicId = resultado.get("public_id");
        if (publicId == null || publicId.toString().isBlank()) {
            throw new IllegalStateException("Cloudinary no devolvió el identificador del documento");
        }
        Object bytes = resultado.get("bytes");
        long tamano = bytes instanceof Number numero ? numero.longValue() : contenido.length;
        return new DocumentoAlmacenado("cloudinary", publicId.toString(), nombreArchivo, contentType, tamano);
    }

    @Override
    public URI enlaceDescarga(DocumentoAlmacenado documento, Duration validez) {
        Map<String, Object> opciones = new LinkedHashMap<>();
        opciones.put("resource_type", "raw");
        opciones.put("type", "private");
        opciones.put("expires_at", Instant.now().plus(validez).getEpochSecond());
        opciones.put("attachment", true);
        try {
            // Para raw el public_id ya incluye la extensión: el formato va vacío.
            return URI.create(cliente.enlacePrivado(documento.referencia(), "", opciones));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el enlace del documento", e);
        }
    }

    @Override
    public byte[] leer(DocumentoAlmacenado documento) {
        // Para verlo en línea el backend trae el archivo por un enlace firmado recién creado.
        URI enlace = enlaceDescarga(documento, Duration.ofMinutes(10));
        try {
            return cliente.descargar(enlace.toString());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el documento de Cloudinary", e);
        }
    }

    @Override
    public void eliminar(DocumentoAlmacenado documento) {
        Map<String, Object> opciones = new LinkedHashMap<>();
        opciones.put("resource_type", "raw");
        opciones.put("type", "private");
        opciones.put("invalidate", true);
        Map<?, ?> resultado;
        try {
            resultado = cliente.destruir(documento.referencia(), opciones);
        } catch (IOException e) {
            throw new AlmacenDocumentosException("No se pudo eliminar el documento de Cloudinary");
        }
        Object estado = resultado == null ? null : resultado.get("result");
        // «not found» es un borrado ya hecho: la operación sigue siendo idempotente.
        if (estado == null || !("ok".equals(estado.toString()) || "not found".equals(estado.toString()))) {
            throw new AlmacenDocumentosException("Cloudinary no confirmó la eliminación del documento");
        }
    }
}
