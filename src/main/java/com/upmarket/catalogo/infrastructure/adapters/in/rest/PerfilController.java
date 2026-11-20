package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.infrastructure.perfil.Perfil;
import com.upmarket.catalogo.infrastructure.perfil.PerfilService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Perfil del usuario autenticado: ver, editar (nombre, celular, foto) y eliminar la cuenta. */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    public record PerfilRequest(String nombre, String telefono, String fotoUrl) { }

    public record PerfilResponse(String usuario, String nombre, String telefono, String fotoUrl) {
        static PerfilResponse de(Perfil p) {
            return new PerfilResponse(p.getUsuario(), p.getNombre(), p.getTelefono(), p.getFotoUrl());
        }
    }

    /** Lo que se ve de otra persona: sin celular. */
    public record PerfilPublico(String nombre, String fotoUrl) {
        static PerfilPublico de(Perfil p) { return new PerfilPublico(p.getNombre(), p.getFotoUrl()); }
    }

    private final PerfilService perfiles;
    private final PublicacionUseCase publicaciones;

    public PerfilController(PerfilService perfiles, PublicacionUseCase publicaciones) {
        this.perfiles = perfiles;
        this.publicaciones = publicaciones;
    }

    @GetMapping
    public PerfilResponse yo() {
        return PerfilResponse.de(perfiles.obtener(ActorActual.obtener().username()));
    }

    @PutMapping
    public PerfilResponse editar(@RequestBody PerfilRequest r) {
        return PerfilResponse.de(
                perfiles.actualizar(ActorActual.obtener().username(), r.nombre(), r.telefono(), r.fotoUrl()));
    }

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilResponse subirFoto(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        return PerfilResponse.de(perfiles.actualizarFoto(ActorActual.obtener().username(), archivo.getBytes()));
    }

    @DeleteMapping("/foto")
    public ResponseEntity<Void> eliminarFoto() {
        perfiles.eliminarFoto(ActorActual.obtener().username());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/publico")
    public PerfilPublico publico(@RequestParam String usuario) {
        return PerfilPublico.de(perfiles.publico(usuario.trim().toLowerCase()));
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminarCuenta(@RequestHeader("Authorization") String authorization) {
        perfiles.eliminarCuenta(ActorActual.obtener(), publicaciones, authorization);
        return ResponseEntity.noContent().build();
    }
}
