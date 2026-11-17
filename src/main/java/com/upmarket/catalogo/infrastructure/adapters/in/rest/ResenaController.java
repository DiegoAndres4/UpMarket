package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.ListadoResenas;
import com.upmarket.catalogo.application.port.in.ResenaUseCase;
import com.upmarket.catalogo.application.port.in.ResenaVista;
import com.upmarket.catalogo.application.port.in.command.CrearResenaCommand;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reseñas de un vendedor: crear la propia (definitiva) y consultar las de cualquiera. */
@RestController
@RequestMapping("/api/perfil/{usuario}/resenas")
public class ResenaController {

    private final ResenaUseCase resenas;

    public ResenaController(ResenaUseCase resenas) {
        this.resenas = resenas;
    }

    public record CrearRequest(Integer estrellas, String comentario) { }

    public record ResenaResponse(String autor, String autorNombre, int estrellas, String comentario,
                                 Instant creadaEn) {
        static ResenaResponse de(ResenaVista vista) {
            return new ResenaResponse(vista.autor(), vista.autorNombre(), vista.estrellas(),
                    vista.comentario(), vista.creadaEn());
        }
    }

    public record ListadoResponse(String vendedor, double promedio, long total, List<ResenaResponse> resenas) { }

    @PostMapping
    public ResponseEntity<ResenaResponse> crear(@PathVariable String usuario, @RequestBody CrearRequest peticion) {
        ResenaVista creada = resenas.crear(ActorActual.obtener(), usuario,
                new CrearResenaCommand(peticion.estrellas(), peticion.comentario()));
        return ResponseEntity.created(URI.create("/api/perfil/" + usuario + "/resenas"))
                .body(ResenaResponse.de(creada));
    }

    @GetMapping
    public ListadoResponse listar(@PathVariable String usuario) {
        ListadoResenas listado = resenas.listar(usuario);
        return new ListadoResponse(listado.vendedor(), listado.promedio(), listado.total(),
                listado.resenas().stream().map(ResenaResponse::de).toList());
    }
}
