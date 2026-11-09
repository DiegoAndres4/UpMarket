package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.domain.exception.FotoNoEncontradaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Foto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Guarda las fotos como archivos en un directorio (en AWS: un volumen EBS/EFS montado en /data/fotos).
 * Valida por el CONTENIDO del archivo (no confía en el nombre ni en el Content-Type que manda el cliente):
 * solo JPG o PNG, hasta 5 MB. Los nombres los genera el servidor (UUID), así que no hay rutas controladas por el usuario.
 */
@Component
public class AlmacenFotosLocal {

    private static final Pattern NOMBRE_VALIDO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    public record Guardada(String nombre, String tipo, long tamanoBytes) { }

    private final Path directorio;

    public AlmacenFotosLocal(@Value("${fotos.directorio:./fotos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de fotos " + this.directorio, e);
        }
    }

    public Guardada guardar(byte[] contenido) {
        if (contenido == null || contenido.length == 0 || contenido.length > Foto.MAXIMO_BYTES) {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        String tipo;
        String extension;
        if (esPng(contenido)) {
            tipo = "image/png";
            extension = "png";
        } else if (esJpeg(contenido)) {
            tipo = "image/jpeg";
            extension = "jpg";
        } else {
            throw new ValorInvalidoException(Foto.mensajeDeLimites());
        }
        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.write(directorio.resolve(nombre), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        }
        return new Guardada(nombre, tipo, contenido.length);
    }

    public Resource leer(String nombre) {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new FotoNoEncontradaException();
        }
        Path archivo = directorio.resolve(nombre).normalize();
        if (!archivo.startsWith(directorio) || !Files.isRegularFile(archivo)) {
            throw new FotoNoEncontradaException();
        }
        return new FileSystemResource(archivo);
    }

    public static String tipoDe(String nombre) {
        return nombre.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean esJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
