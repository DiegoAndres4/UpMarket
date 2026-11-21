package com.upmarket.catalogo.application.port.in.admin;

import com.upmarket.catalogo.domain.model.Actor;

/**
 * Documentos del panel de administración: listado de pagos, recibo de pago y descarga de archivos.
 * Todas las operaciones exigen el rol ADMIN.
 */
public interface DocumentosUseCase {

    PaginaAdmin<PagoAdmin> pagos(Actor actor, int pagina, int tamano);

    /** Idempotente: si el recibo ya existe lo devuelve con {@code nuevo=false}. */
    ResultadoRecibo generarRecibo(Actor actor, long pagoId);

    /**
     * Recibo de una publicación visto por su autor (o un ADMIN). Si aún no existe se genera con el
     * mismo servicio idempotente del panel; nunca se genera dentro de la transacción del pago.
     */
    ContenidoDocumento reciboDePublicacion(Actor actor, long publicacionId);

    DescargaDocumento descargar(Actor actor, long documentoId);

    /** Bytes para ver el documento en línea; el backend los entrega siempre, sin enlaces. */
    ContenidoDocumento contenido(Actor actor, long documentoId);

    /** Genera PDF y XML desde UNA sola foto de {@code AdminService.estadisticas}. */
    ReporteGenerado generarReporteEstadisticas(Actor actor);

    /** Historial agrupado por número: cada reporte con sus descargas PDF y XML. */
    PaginaAdmin<ReporteAdmin> reportes(Actor actor, int pagina, int tamano);

    /**
     * Elimina un reporte completo (PDF y XML). Primero borra los archivos del almacén y solo después
     * las filas; si el almacén falla, las filas se conservan. Solo ADMIN y solo reportes.
     */
    void eliminarReporte(Actor actor, String numero);
}
