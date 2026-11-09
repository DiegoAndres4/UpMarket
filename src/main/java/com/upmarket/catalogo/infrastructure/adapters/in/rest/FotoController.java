package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.infrastructure.adapters.in.rest.dto.FotoDto;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida y entrega de fotos. Subir exige estar autenticado; ver una foto es público (así funciona la etiqueta img).
 * El resultado de subir es un {@link FotoDto} listo para enviarse dentro de {@code fotos} al publicar.
 */
@RestController
@RequestMapping("/api/fotos")
public class FotoController {

    private final AlmacenFotosLocal almacen;

    public FotoController(AlmacenFotosLocal almacen) {
        this.almacen = almacen;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoDto> subir(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        ActorActual.obtener(); // exige un usuario autenticado
        var g = almacen.guardar(archivo.getBytes());
        var foto = new FotoDto("/api/fotos/" + g.nombre(), g.tipo(), g.tamanoBytes());
        return ResponseEntity.created(URI.create(foto.url())).body(foto);
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<Resource> ver(@PathVariable String nombre) {
        Resource recurso = almacen.leer(nombre);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AlmacenFotosLocal.tipoDe(nombre)))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(recurso);
    }
}
