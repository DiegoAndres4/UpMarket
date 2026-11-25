package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.Interesado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Fila de {@code publicacion_interes}. La clave única impide duplicar a la misma persona. */
@Entity
@Table(name = "publicacion_interes",
        uniqueConstraints = @UniqueConstraint(name = "uk_interes_publicacion_usuario",
                columnNames = {"publicacion_id", "usuario"}))
public class InteresJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publicacion_id", nullable = false)
    private long publicacionId;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Column(nullable = false)
    private int veces;

    @Column(name = "ultima_vez", nullable = false)
    private Instant ultimaVez;

    protected InteresJpaEntity() {
    }

    public InteresJpaEntity(long publicacionId, String usuario, Instant ultimaVez) {
        this.publicacionId = publicacionId;
        this.usuario = usuario;
        this.veces = 1;
        this.ultimaVez = ultimaVez;
    }

    public void registrar(Instant ahora) {
        this.veces++;
        this.ultimaVez = ahora;
    }

    public Interesado aDominio() {
        return new Interesado(usuario, veces, ultimaVez);
    }
}
