package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import java.util.List;
import java.util.Optional;

/** Persistencia de los documentos generados por el panel de administración. */
public interface DocumentoRepositoryPort {

    /**
     * Inserta el documento. Si llega sin número, el adaptador lo deriva del id asignado
     * ({@code REC-000001}, {@code EST-000001}). Lanza {@code DocumentoDuplicadoException} si
     * choca con la clave única de recibos.
     */
    DocumentoGenerado guardar(DocumentoGenerado documento);

    /** Actualiza los datos de almacenamiento y el nombre del archivo ya reservado. */
    DocumentoGenerado actualizar(DocumentoGenerado documento);

    Optional<DocumentoGenerado> buscarPorId(long id);

    /** Recibo PDF de un pago, si ya se generó. */
    Optional<DocumentoGenerado> buscarRecibo(long pagoId);

    /** Todos los recibos, para completar el listado de pagos. */
    List<DocumentoGenerado> recibos();

    /** Reportes de estadísticas (PDF y XML), más recientes primero. */
    List<DocumentoGenerado> reportes();

    /** Las dos filas (PDF y XML) de un reporte, identificadas por su número {@code EST-…}. */
    List<DocumentoGenerado> reportesPorNumero(String numero);

    /** Borra en una sola transacción todas las filas indicadas. */
    void eliminarPorIds(List<Long> ids);
}
