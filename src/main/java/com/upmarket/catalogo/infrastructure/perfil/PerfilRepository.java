package com.upmarket.catalogo.infrastructure.perfil;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilRepository extends JpaRepository<Perfil, String> {

    boolean existsByUsuarioAndEliminadaTrue(String usuario);
}
