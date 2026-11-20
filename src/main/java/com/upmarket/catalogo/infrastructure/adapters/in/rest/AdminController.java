package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.admin.AdminUseCase;
import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin;
import com.upmarket.catalogo.application.port.in.admin.FiltroAdminPublicaciones;
import com.upmarket.catalogo.application.port.in.admin.PaginaAdmin;
import com.upmarket.catalogo.application.port.in.admin.PublicacionAdmin;
import com.upmarket.catalogo.application.port.in.admin.UsuarioAdmin;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Panel de administración. Spring Security exige el rol ADMIN para todo /api/admin/**. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUseCase admin;

    public AdminController(AdminUseCase admin) {
        this.admin = admin;
    }

    @GetMapping("/estadisticas")
    public EstadisticasAdmin estadisticas() {
        return admin.estadisticas(ActorActual.obtener());
    }

    @GetMapping("/publicaciones")
    public PaginaAdmin<PublicacionAdmin> publicaciones(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) EstadoPublicacion estadoPublicacion,
            @RequestParam(required = false) EstadoPago estadoPago,
            @RequestParam(required = false) String vendedor,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return admin.publicaciones(ActorActual.obtener(), new FiltroAdminPublicaciones(texto, categoria,
                estadoPublicacion, estadoPago, vendedor, pagina, tamano));
    }

    @DeleteMapping("/publicaciones/{id}")
    public ResponseEntity<Void> eliminarPublicacion(@PathVariable long id) {
        admin.eliminarPublicacion(ActorActual.obtener(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuarios")
    public PaginaAdmin<UsuarioAdmin> usuarios(@RequestParam(defaultValue = "1") int pagina,
                                              @RequestParam(defaultValue = "20") int tamano) {
        return admin.usuarios(ActorActual.obtener(), pagina, tamano);
    }
}
