package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.infrastructure.adapters.in.rest.dto.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HU-9. El controlador solo traduce HTTP a casos de uso: no contiene reglas de negocio ni de permisos. */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaUseCase categorias;

    public CategoriaController(CategoriaUseCase categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categorias.listar().stream().map(CategoriaResponse::de).toList();
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> crear(@RequestBody CategoriaRequest r) {
        var c = categorias.crear(ActorActual.obtener(), r.nombre());
        return ResponseEntity.created(URI.create("/api/categorias")).body(CategoriaResponse.de(c));
    }
}
