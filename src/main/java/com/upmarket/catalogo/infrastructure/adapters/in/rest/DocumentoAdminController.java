package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.admin.ContenidoDocumento;
import com.upmarket.catalogo.application.port.in.admin.DescargaDocumento;
import com.upmarket.catalogo.application.port.in.admin.DocumentoAdmin;
import com.upmarket.catalogo.application.port.in.admin.DocumentosUseCase;
import com.upmarket.catalogo.application.port.in.admin.PaginaAdmin;
import com.upmarket.catalogo.application.port.in.admin.PagoAdmin;
import com.upmarket.catalogo.application.port.in.admin.ReporteAdmin;
import com.upmarket.catalogo.application.port.in.admin.ReporteGenerado;
import com.upmarket.catalogo.application.port.in.admin.ResultadoRecibo;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagos y documentos del panel. Spring Security ya exige ADMIN para todo /api/admin/**; el servicio
 * lo vuelve a comprobar en el dominio. El token nunca viaja en la URL: la descarga remota se firma
 * con caducidad y la local se sirve como adjunto por este mismo endpoint.
 */
@RestController
@RequestMapping("/api/admin")
public class DocumentoAdminController {

    private final DocumentosUseCase documentos;

    public DocumentoAdminController(DocumentosUseCase documentos) {
        this.documentos = documentos;
    }

    @GetMapping("/pagos")
    public PaginaAdmin<PagoAdmin> pagos(@RequestParam(defaultValue = "1") int pagina,
                                        @RequestParam(defaultValue = "20") int tamano) {
        return documentos.pagos(ActorActual.obtener(), pagina, tamano);
    }

    @PostMapping("/pagos/{pagoId}/recibo")
    public ResponseEntity<DocumentoAdmin> recibo(
            @PathVariable long pagoId) {
        ResultadoRecibo resultado = documentos.generarRecibo(ActorActual.obtener(), pagoId);
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.documento());
    }

    @PostMapping("/reportes/estadisticas")
    public ResponseEntity<ReporteGenerado> generarReporte() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentos.generarReporteEstadisticas(ActorActual.obtener()));
    }

    @GetMapping("/reportes")
    public PaginaAdmin<ReporteAdmin> reportes(@RequestParam(defaultValue = "1") int pagina,
                                              @RequestParam(defaultValue = "20") int tamano) {
        return documentos.reportes(ActorActual.obtener(), pagina, tamano);
    }

    @GetMapping("/documentos/{id}/descarga")
    public ResponseEntity<?> descargar(@PathVariable long id,
                                       @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acepta) {
        DescargaDocumento descarga = documentos.descargar(ActorActual.obtener(), id);
        if (descarga.enlace() != null) {
            boolean pideJson = acepta != null
                    && acepta.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
            if (pideJson) {
                return ResponseEntity.ok(Map.of("enlace", descarga.enlace().toString(),
                        "nombreArchivo", descarga.nombreArchivo()));
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(descarga.enlace()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(descarga.contenido());
    }

    /** Ver en línea: mismos permisos y búsqueda que la descarga, pero {@code inline} y sin caché. */
    @GetMapping("/documentos/{id}/contenido")
    public ResponseEntity<byte[]> contenido(@PathVariable long id) {
        ContenidoDocumento documento = documentos.contenido(ActorActual.obtener(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(documento.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(documento.contenido());
    }
}
