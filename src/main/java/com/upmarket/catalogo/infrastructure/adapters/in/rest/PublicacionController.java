package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.PagoUseCase;
import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.infrastructure.adapters.in.rest.dto.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HU-8, HU-10, HU-11 y HU-12. Solo traduce HTTP a casos de uso: las reglas y los permisos viven en el dominio. */
@RestController
@RequestMapping("/api/publicaciones")
public class PublicacionController {

    private final PublicacionUseCase publicaciones;
    private final PagoUseCase pagos;

    public PublicacionController(PublicacionUseCase publicaciones, PagoUseCase pagos) {
        this.publicaciones = publicaciones;
        this.pagos = pagos;
    }

    @PostMapping
    public ResponseEntity<PublicacionResponse> publicar(@RequestBody PublicacionRequest r) {
        List<FotoComando> fotos = r.fotos() == null ? null
                : r.fotos().stream().map(f -> new FotoComando(f.url(), f.tipo(), f.tamanoBytes())).toList();
        var actor = ActorActual.obtener();
        // Si la cuenta se registró con un correo, ese correo es el único de contacto posible: no se puede cambiar.
        boolean pideCorreo = r.correo() != null && !r.correo().isBlank();
        String correo = pideCorreo && actor.username().contains("@") ? actor.username() : r.correo();
        var p = publicaciones.publicar(actor,
                new PublicarCommand(r.titulo(), r.descripcion(), r.precio(), r.estado(), r.categoria(), fotos,
                        r.whatsapp(), correo));
        return ResponseEntity.created(URI.create("/api/publicaciones/" + p.getId())).body(PublicacionResponse.de(p));
    }

    /** {@code texto} (o {@code q}) busca en título y descripción; {@code estado} es NUEVO o USADO. */
    @GetMapping
    public List<PublicacionResponse> buscar(@RequestParam(required = false) String texto,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) String precioMin,
                                            @RequestParam(required = false) String precioMax,
                                            @RequestParam(required = false) String estado) {
        var consulta = new BuscarQuery(texto != null ? texto : q, categoria, precioMin, precioMax, estado);
        return publicaciones.buscar(consulta).stream().map(PublicacionResponse::de).toList();
    }

    @GetMapping("/mias")
    public List<PublicacionResponse> mias() {
        return publicaciones.mias(ActorActual.obtener()).stream()
                .map(p -> PublicacionResponse.de(p, p.getVistas()))
                .toList();
    }

    @GetMapping("/{id}")
    public PublicacionResponse detalle(@PathVariable long id) {
        var actor = ActorActual.obtener();
        var p = publicaciones.detalle(actor, id);
        boolean puedeVerVistas = actor.esAdmin() || actor.username().equals(p.getVendedor());
        return puedeVerVistas ? PublicacionResponse.de(p, p.getVistas()) : PublicacionResponse.de(p);
    }

    @GetMapping("/{id}/contacto")
    public ContactoResponse contacto(@PathVariable long id) {
        return ContactoResponse.de(publicaciones.contacto(ActorActual.obtener(), id));
    }

    /** Cobro simulado de la tarifa de publicación. Solo el autor puede pagar su propia publicación. */
    @PostMapping("/{id}/pago")
    public PagoResponse pagar(@PathVariable long id) {
        return PagoResponse.de(pagos.pagar(ActorActual.obtener(), id));
    }

    @PutMapping("/{id}")
    public PublicacionResponse editar(@PathVariable long id, @RequestBody EdicionRequest r) {
        return PublicacionResponse.de(publicaciones.editar(ActorActual.obtener(),
                new EditarPublicacionCommand(id, r.titulo(), r.descripcion(), r.precio(), r.estado(), r.categoria())));
    }

    @PostMapping("/{id}/sold")
    public PublicacionResponse marcarVendida(@PathVariable long id) {
        return PublicacionResponse.de(publicaciones.marcarVendida(ActorActual.obtener(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable long id) {
        publicaciones.eliminar(ActorActual.obtener(), id);
        return ResponseEntity.noContent().build();
    }
}
