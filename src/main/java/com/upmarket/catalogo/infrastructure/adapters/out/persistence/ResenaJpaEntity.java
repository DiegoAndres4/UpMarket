package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.Resena;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Fila de la tabla {@code resena}. La restricción única impide dos reseñas del mismo autor al mismo vendedor. */
@Entity
@Table(name = "resena",
        uniqueConstraints = @UniqueConstraint(name = "uk_resena_vendedor_autor",
                columnNames = {"vendedor", "autor"}))
public class ResenaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String vendedor;

    @Column(nullable = false, length = 120)
    private String autor;

    @Column(nullable = false)
    private int estrellas;

    @Column(nullable = false, length = 500)
    private String comentario;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    protected ResenaJpaEntity() { }

    public ResenaJpaEntity(Resena resena) {
        this.vendedor = resena.vendedor();
        this.autor = resena.autor();
        this.estrellas = resena.estrellas();
        this.comentario = resena.comentario();
        this.creadaEn = resena.creadaEn();
    }

    public Resena aDominio() {
        return new Resena(id, vendedor, autor, estrellas, comentario, creadaEn);
    }
}
