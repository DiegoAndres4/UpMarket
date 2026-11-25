package com.upmarket.catalogo.infrastructure.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface FotoSubidaRepository extends JpaRepository<FotoSubida, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FotoSubida f where f.url = :url")
    Optional<FotoSubida> bloquear(@Param("url") String url);
    List<FotoSubida> findByEstadoOrderByIdAsc(String estado);
}
