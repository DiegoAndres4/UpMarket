package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.FormatoDocumento;
import com.upmarket.catalogo.domain.model.TipoDocumento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoSpringJpaRepository extends JpaRepository<DocumentoGeneradoJpaEntity, Long> {

    Optional<DocumentoGeneradoJpaEntity> findFirstByTipoAndFormatoAndPagoId(TipoDocumento tipo,
                                                                           FormatoDocumento formato, Long pagoId);

    List<DocumentoGeneradoJpaEntity> findByTipoOrderByGeneradoEnDescIdDesc(TipoDocumento tipo);
}
