package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.admin.DescargaDocumento;
import com.upmarket.catalogo.application.port.in.admin.DocumentoAdmin;
import com.upmarket.catalogo.application.port.in.admin.DocumentosUseCase;
import com.upmarket.catalogo.application.port.in.admin.PaginaAdmin;
import com.upmarket.catalogo.application.port.in.admin.PagoAdmin;
import com.upmarket.catalogo.application.port.in.admin.PagoAdmin.ResumenComprador;
import com.upmarket.catalogo.application.port.in.admin.PagoAdmin.ResumenPublicacion;
import com.upmarket.catalogo.application.port.in.admin.PagoAdmin.ResumenRecibo;
import com.upmarket.catalogo.application.port.in.admin.ResultadoRecibo;
import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DatosRecibo;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import com.upmarket.catalogo.application.port.out.DocumentoRepositoryPort;
import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.GeneradorDocumentosPort;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PerfilAdmin;
import com.upmarket.catalogo.application.port.out.PerfilAdminPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.exception.DocumentoDuplicadoException;
import com.upmarket.catalogo.domain.exception.DocumentoNoEncontradoException;
import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.PagoNoEncontradoException;
import com.upmarket.catalogo.domain.exception.PublicacionNoEncontradaException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Genera y sirve los documentos del panel: recibo de un pago (PDF) y, más adelante, reportes de
 * estadísticas. El binario vive en un almacén privado; aquí solo se decide qué se genera y cómo se
 * entrega. Las reglas de acceso se delegan en {@link PoliticaAcceso}.
 */
public class DocumentosService implements DocumentosUseCase {

    /** Los enlaces firmados de Cloudinary valen diez minutos, como máximo. */
    private static final Duration VALIDEZ_ENLACE = Duration.ofMinutes(10);

    private final PagoRepositoryPort pagos;
    private final PublicacionRepositoryPort publicaciones;
    private final PerfilAdminPort perfiles;
    private final DocumentoRepositoryPort documentos;
    private final AlmacenDocumentosPort almacen;
    private final GeneradorDocumentosPort generador;
    private final PoliticaAcceso acceso;
    private final Clock reloj;

    public DocumentosService(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones,
                             PerfilAdminPort perfiles, DocumentoRepositoryPort documentos,
                             AlmacenDocumentosPort almacen, GeneradorDocumentosPort generador,
                             PoliticaAcceso acceso, Clock reloj) {
        this.pagos = pagos;
        this.publicaciones = publicaciones;
        this.perfiles = perfiles;
        this.documentos = documentos;
        this.almacen = almacen;
        this.generador = generador;
        this.acceso = acceso;
        this.reloj = reloj;
    }

    @Override
    public PaginaAdmin<PagoAdmin> pagos(Actor actor, int pagina, int tamano) {
        acceso.asegurarAdmin(actor, "ver los pagos");

        List<Pago> lista = new ArrayList<>(pagos.todos());
        lista.sort(Comparator.comparing(Pago::getCreadoEn).reversed()
                .thenComparing(Pago::getId, Comparator.reverseOrder()));

        Map<Long, Publicacion> publicacionesPorId = new HashMap<>();
        for (Publicacion publicacion : publicaciones.buscar(
                new FiltroPublicacion(null, null, null, null, null, null, null, null))) {
            publicacionesPorId.put(publicacion.getId(), publicacion);
        }
        Map<String, PerfilAdmin> perfilesPorUsuario = new HashMap<>();
        for (PerfilAdmin perfil : perfiles.listar()) {
            perfilesPorUsuario.put(perfil.usuario(), perfil);
        }
        Map<Long, DocumentoGenerado> recibosPorPago = new HashMap<>();
        for (DocumentoGenerado recibo : documentos.recibos()) {
            if (recibo.pagoId() != null) {
                recibosPorPago.put(recibo.pagoId(), recibo);
            }
        }

        List<PagoAdmin> filas = lista.stream()
                .map(pago -> fila(pago, publicacionesPorId.get(pago.getPublicacionId()),
                        perfilesPorUsuario, recibosPorPago.get(pago.getId())))
                .toList();
        return PaginaAdmin.de(filas, pagina, tamano);
    }

    private static PagoAdmin fila(Pago pago, Publicacion publicacion, Map<String, PerfilAdmin> perfiles,
                                  DocumentoGenerado recibo) {
        String usuario = publicacion == null ? null : publicacion.getVendedor();
        PerfilAdmin perfil = usuario == null ? null : perfiles.get(usuario);
        ResumenPublicacion resumenPublicacion = publicacion == null ? null
                : new ResumenPublicacion(publicacion.getId(), publicacion.getTitulo());
        ResumenComprador comprador = usuario == null ? null
                : new ResumenComprador(usuario, perfil == null ? usuario : perfil.nombre());
        ResumenRecibo resumenRecibo = recibo == null ? new ResumenRecibo(false, null, null)
                : new ResumenRecibo(true, recibo.numero(), recibo.generadoEn());
        return new PagoAdmin(pago.getId(), pago.getReferencia(), pago.getMonto(), pago.getEstado(),
                pago.getCreadoEn(), pago.getAprobadoEn(), resumenPublicacion, comprador, resumenRecibo);
    }

    @Override
    public ResultadoRecibo generarRecibo(Actor actor, long pagoId) {
        acceso.asegurarAdmin(actor, "generar recibos de pago");

        Optional<DocumentoGenerado> existente = documentos.buscarRecibo(pagoId);
        if (existente.isPresent() && existente.get().almacenado()) {
            return new ResultadoRecibo(false, DocumentoAdmin.de(existente.get()));
        }

        Pago pago = pagos.buscarPorId(pagoId).orElseThrow(() -> new PagoNoEncontradoException(pagoId));
        if (pago.getEstado() != EstadoPago.APROBADO) {
            throw new EstadoInvalidoException("Solo los pagos aprobados tienen recibo");
        }
        Publicacion publicacion = publicaciones.buscarPorId(pago.getPublicacionId())
                .orElseThrow(() -> new PublicacionNoEncontradaException(pago.getPublicacionId()));
        PerfilAdmin perfil = perfiles.buscar(publicacion.getVendedor()).orElse(null);
        String nombreComprador = perfil == null ? publicacion.getVendedor() : perfil.nombre();
        String correoComprador = perfil == null ? publicacion.getVendedor() : perfil.correo();

        boolean nuevo = existente.isEmpty();
        DocumentoGenerado fila = existente.orElse(null);
        if (fila == null) {
            try {
                fila = documentos.guardar(DocumentoGenerado.nuevo(TipoDocumento.RECIBO_PAGO,
                        FormatoDocumento.PDF, pagoId, actor.username(), reloj.instant()));
            } catch (DocumentoDuplicadoException e) {
                // Otra petición ganó la carrera: se devuelve (o se completa) su fila.
                nuevo = false;
                fila = documentos.buscarRecibo(pagoId).orElseThrow(() -> e);
            }
        }

        if (!fila.almacenado()) {
            DatosRecibo datos = new DatosRecibo(pagoId, fila.numero(), publicacion.getTitulo(), nombreComprador,
                    correoComprador, pago.getMonto(), pago.getReferencia(), pago.getAprobadoEn(),
                    reloj.instant());
            byte[] pdf = generador.reciboPago(datos);
            String nombreArchivo = "upmarket-recibo-" + fila.numero() + ".pdf";
            DocumentoAlmacenado guardado = almacen.guardar(pdf, nombreArchivo,
                    AlmacenDocumentosPort.CARPETA_RECIBOS, "application/pdf");
            fila = documentos.actualizar(fila.conAlmacenamiento(guardado.proveedor(), guardado.referencia(),
                    nombreArchivo, pdf.length));
        }
        return new ResultadoRecibo(nuevo, DocumentoAdmin.de(fila));
    }

    @Override
    public DescargaDocumento descargar(Actor actor, long documentoId) {
        acceso.asegurarAdmin(actor, "descargar documentos");
        DocumentoGenerado documento = documentos.buscarPorId(documentoId)
                .orElseThrow(() -> new DocumentoNoEncontradoException(documentoId));
        if (!documento.almacenado()) {
            throw new DocumentoNoEncontradoException(documentoId);
        }
        String contentType = documento.formato() == FormatoDocumento.XML ? "application/xml" : "application/pdf";
        DocumentoAlmacenado guardado = new DocumentoAlmacenado(documento.proveedor(), documento.referencia(),
                documento.nombreArchivo(), contentType, documento.tamanoBytes());
        URI enlace = almacen.enlaceDescarga(guardado, VALIDEZ_ENLACE);
        if (enlace != null) {
            return new DescargaDocumento(enlace, null, documento.nombreArchivo(), contentType);
        }
        return new DescargaDocumento(null, almacen.leer(guardado), documento.nombreArchivo(), contentType);
    }
}
