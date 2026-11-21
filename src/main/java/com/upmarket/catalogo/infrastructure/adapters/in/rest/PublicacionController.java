package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.PagoUseCase;
import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.admin.ContenidoDocumento;
import com.upmarket.catalogo.application.port.in.admin.DocumentosUseCase;
import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.infrastructure.adapters.in.rest.dto.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HU-8, HU-10, HU-11 y HU-12. Solo traduce HTTP a casos de uso: las reglas y los permisos viven en el dominio. */
@RestController
@RequestMapping("/api/publicaciones")
public class PublicacionController {

    private final PublicacionUseCase publicaciones;
    private final PagoUseCase pagos;
    private final DocumentosUseCase documentos;

    public PublicacionController(PublicacionUseCase publicaciones, PagoUseCase pagos,
                                 DocumentosUseCase documentos) {
        this.publicaciones = publicaciones;
        this.pagos = pagos;
        this.documentos = documentos;
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

    public record OrdenPaypalResponse(String orderId) { }

    public record CapturaPaypalRequest(String orderId) { }

    /** Crea la orden de PayPal (Orders v2). Solo el autor; el monto USD lo fija la configuración. */
    @PostMapping("/{id}/pago/paypal/orden")
    public OrdenPaypalResponse ordenPaypal(@PathVariable long id) {
        return new OrdenPaypalResponse(pagos.crearOrdenPaypal(ActorActual.obtener(), id));
    }

    /** Captura la orden de PayPal y, si todo coincide, aprueba el pago y activa la publicación. */
    @PostMapping("/{id}/pago/paypal/captura")
    public PagoResponse capturarPaypal(@PathVariable long id, @RequestBody CapturaPaypalRequest peticion) {
        return PagoResponse.de(pagos.capturarPaypal(ActorActual.obtener(), id, peticion.orderId()));
    }

    /**
     * Recibo del pago aprobado, para el autor o un ADMIN. Se muestra en línea (iframe) salvo con
     * {@code ?descargar=true}, que lo entrega como adjunto. Si el recibo no existe se genera aquí.
     */
    @GetMapping("/{id}/recibo")
    public ResponseEntity<byte[]> recibo(@PathVariable long id,
                                         @RequestParam(defaultValue = "false") boolean descargar) {
        ContenidoDocumento documento = documentos.reciboDePublicacion(ActorActual.obtener(), id);
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(documento.nombreArchivo(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(documento.contenido());
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
