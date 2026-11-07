package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.Categoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "categoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_nombre", columnNames = "nombre_normalizado"))
public class CategoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(name = "nombre_normalizado", nullable = false, length = 40)
    private String nombreNormalizado;

    protected CategoriaJpaEntity() {
    }

    public CategoriaJpaEntity(Long id, String nombre, String nombreNormalizado) {
        this.id = id;
        this.nombre = nombre;
        this.nombreNormalizado = nombreNormalizado;
    }

    public Categoria aDominio() {
        return Categoria.reconstruir(id, nombre, nombreNormalizado);
    }
}
