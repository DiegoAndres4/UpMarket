package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.application.port.out.DocumentoRepositoryPort;
import com.upmarket.catalogo.domain.exception.DocumentoDuplicadoException;
import com.upmarket.catalogo.domain.exception.DocumentoNoEncontradoException;
import com.upmarket.catalogo.domain.model.DocumentoGenerado;
import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentoRepositoryJpaAdapter implements DocumentoRepositoryPort {

    private final DocumentoSpringJpaRepository jpa;

    public DocumentoRepositoryJpaAdapter(DocumentoSpringJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DocumentoGenerado guardar(DocumentoGenerado documento) {
        DocumentoGeneradoJpaEntity entidad;
        try {
            entidad = jpa.saveAndFlush(new DocumentoGeneradoJpaEntity(documento));
        } catch (DataIntegrityViolationException e) {
            throw new DocumentoDuplicadoException("Ya existe un documento con la misma clave");
        }
        if (entidad.getNumero() == null) {
            entidad.setNumero(prefijo(documento.tipo()) + String.format("%06d", entidad.getId()));
            entidad = jpa.saveAndFlush(entidad);
        }
        return entidad.aDominio();
    }

    @Override
    public DocumentoGenerado actualizar(DocumentoGenerado documento) {
        DocumentoGeneradoJpaEntity entidad = jpa.findById(documento.id())
                .orElseThrow(() -> new DocumentoNoEncontradoException(documento.id()));
        entidad.aplicar(documento);
        return jpa.saveAndFlush(entidad).aDominio();
    }

    @Override
    public Optional<DocumentoGenerado> buscarPorId(long id) {
        return jpa.findById(id).map(DocumentoGeneradoJpaEntity::aDominio);
    }

    @Override
    public Optional<DocumentoGenerado> buscarRecibo(long pagoId) {
        return jpa.findFirstByTipoAndFormatoAndPagoId(TipoDocumento.RECIBO_PAGO, FormatoDocumento.PDF, pagoId)
                .map(DocumentoGeneradoJpaEntity::aDominio);
    }

    @Override
    public List<DocumentoGenerado> recibos() {
        return jpa.findByTipoOrderByGeneradoEnDescIdDesc(TipoDocumento.RECIBO_PAGO).stream()
                .map(DocumentoGeneradoJpaEntity::aDominio).toList();
    }

    @Override
    public List<DocumentoGenerado> reportes() {
        return jpa.findByTipoOrderByGeneradoEnDescIdDesc(TipoDocumento.REPORTE_ESTADISTICAS).stream()
                .map(DocumentoGeneradoJpaEntity::aDominio).toList();
    }

    @Override
    public List<DocumentoGenerado> reportesPorNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return List.of();
        }
        return jpa.findByTipoAndNumeroOrderByFormatoAsc(TipoDocumento.REPORTE_ESTADISTICAS, numero).stream()
                .map(DocumentoGeneradoJpaEntity::aDominio).toList();
    }

    @Override
    @Transactional
    public void eliminarPorIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jpa.deleteAllByIdInBatch(ids);
    }

    private static String prefijo(TipoDocumento tipo) {
        return tipo == TipoDocumento.RECIBO_PAGO ? "REC-" : "EST-";
    }
}
