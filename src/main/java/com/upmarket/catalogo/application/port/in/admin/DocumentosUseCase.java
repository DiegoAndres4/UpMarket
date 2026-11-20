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

    DescargaDocumento descargar(Actor actor, long documentoId);
}
