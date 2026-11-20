package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
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
 * Subida y entrega de fotos. Subir exige estar autenticado; ver una foto local es público
 * (así funciona la etiqueta img). El resultado de subir es un {@link FotoDto} listo para enviarse
 * dentro de {@code fotos} al publicar. Con Cloudinary la URL es absoluta y no pasa por este controlador.
 */
@RestController
@RequestMapping("/api/fotos")
public class FotoController {

    private final AlmacenFotosPort almacen;
    private final AlmacenFotosLocal local;

    public FotoController(AlmacenFotosPort almacen, AlmacenFotosLocal local) {
        this.almacen = almacen;
        this.local = local;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoDto> subir(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        ActorActual.obtener(); // exige un usuario autenticado
        var foto = almacen.guardar(archivo.getBytes(), AlmacenFotosPort.CARPETA_PUBLICACIONES);
        var dto = FotoDto.de(foto);
        return ResponseEntity.created(URI.create(dto.url())).body(dto);
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<Resource> ver(@PathVariable String nombre) {
        Resource recurso = local.leer(nombre);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AlmacenFotosLocal.tipoDe(nombre)))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(recurso);
    }
}
