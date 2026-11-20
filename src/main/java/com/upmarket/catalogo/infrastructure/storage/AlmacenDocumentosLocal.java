package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import com.upmarket.catalogo.domain.exception.AlmacenDocumentosException;
import com.upmarket.catalogo.domain.exception.DocumentoNoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Documentos en disco (en Docker/AWS: volumen montado en /data/documentos). Los nombres los genera el
 * servidor y solo se sirven por el endpoint ADMIN de descarga, nunca por {@code /api/fotos}.
 */
@Component
public class AlmacenDocumentosLocal implements AlmacenDocumentosPort {

    private static final Pattern REFERENCIA_VALIDA = Pattern.compile("[0-9a-f-]{36}\\.(pdf|xml)");

    private final Path directorio;

    public AlmacenDocumentosLocal(@Value("${upmarket.documentos.directorio:./documentos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de documentos " + this.directorio, e);
        }
    }

    @Override
    public DocumentoAlmacenado guardar(byte[] contenido, String nombreArchivo, String carpeta, String contentType) {
        String extension = nombreArchivo != null && nombreArchivo.toLowerCase().endsWith(".xml") ? "xml" : "pdf";
        String referencia = UUID.randomUUID() + "." + extension;
        try {
            Files.write(directorio.resolve(referencia), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el documento", e);
        }
        return new DocumentoAlmacenado("local", referencia, nombreArchivo, contentType, contenido.length);
    }

    @Override
    public URI enlaceDescarga(DocumentoAlmacenado documento, Duration validez) {
        return null; // el backend sirve el archivo por streaming
    }

    @Override
    public byte[] leer(DocumentoAlmacenado documento) {
        String referencia = documento.referencia();
        if (referencia == null || !REFERENCIA_VALIDA.matcher(referencia).matches()) {
            throw new DocumentoNoEncontradoException(0L);
        }
        Path archivo = directorio.resolve(referencia).normalize();
        if (!archivo.startsWith(directorio) || !Files.isRegularFile(archivo)) {
            throw new DocumentoNoEncontradoException(0L);
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento", e);
        }
    }

    @Override
    public void eliminar(DocumentoAlmacenado documento) {
        String referencia = documento.referencia();
        // Una referencia inválida o un archivo ausente cuentan como ya eliminados.
        if (referencia == null || !REFERENCIA_VALIDA.matcher(referencia).matches()) {
            return;
        }
        Path archivo = directorio.resolve(referencia).normalize();
        if (!archivo.startsWith(directorio)) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new AlmacenDocumentosException("No se pudo eliminar el documento del disco");
        }
    }
}
