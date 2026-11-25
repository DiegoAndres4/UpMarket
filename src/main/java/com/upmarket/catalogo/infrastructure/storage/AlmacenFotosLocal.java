package com.upmarket.catalogo.infrastructure.storage;

import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.domain.exception.FotoNoEncontradaException;
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
public class AlmacenFotosLocal implements AlmacenFotosPort {

    private static final Pattern NOMBRE_VALIDO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    private static final String PREFIJO_PUBLICO = "/api/fotos/";

    private final Path directorio;

    public AlmacenFotosLocal(@Value("${fotos.directorio:./fotos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de fotos " + this.directorio, e);
        }
    }

    @Override
    public Foto guardar(byte[] contenido, String carpeta) {
        String tipo = FotosValidadas.tipoDeContenido(contenido);
        String nombre = UUID.randomUUID() + "." + FotosValidadas.extensionDe(tipo);
        try {
            Files.write(directorio.resolve(nombre), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        }
        return new Foto(PREFIJO_PUBLICO + nombre, tipo, contenido.length);
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

    /**
     * Borra un archivo propio a partir de su nombre o de la URL con la que se sirve
     * ({@code /api/fotos/<nombre>}). Si el valor no es un nombre local válido (por ejemplo una
     * URL de Cloudinary), no hace nada.
     */
    @Override
    public void borrar(String url) {
        borrarConResultado(url);
    }

    @Override
    public boolean borrarConResultado(String url) {
        String nombre = nombreLocal(url);
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            return false;
        }
        try {
            Files.deleteIfExists(directorio.resolve(nombre).normalize());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String tipoDe(String nombre) {
        return nombre.endsWith(".png") ? FotosValidadas.PNG : FotosValidadas.JPEG;
    }

    private static String nombreLocal(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        int marca = limpio.indexOf(PREFIJO_PUBLICO);
        return marca >= 0 ? limpio.substring(marca + PREFIJO_PUBLICO.length()) : limpio;
    }
}
