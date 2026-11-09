package com.upmarket.catalogo.infrastructure.adapters.out.persistence;

import com.upmarket.catalogo.domain.model.Contacto;
import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.domain.model.Publicacion;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "publicacion")
public class PublicacionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Column(name = "texto_busqueda", nullable = false, length = 2200)
    private String textoBusqueda;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_articulo", nullable = false, length = 20)
    private EstadoArticulo estadoArticulo;

    @Column(nullable = false, length = 40)
    private String categoria;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "publicacion_foto", joinColumns = @JoinColumn(name = "publicacion_id"))
    @OrderColumn(name = "orden")
    private List<FotoEmbeddable> fotos = new ArrayList<>();

    @Column(length = 30)
    private String whatsapp;

    @Column(length = 120)
    private String correo;

    @Column(nullable = false, length = 80)
    private String vendedor;

    @Column(name = "publicada_en", nullable = false)
    private Instant publicadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPublicacion estado;

    @Column(name = "vendida_en")
    private Instant vendidaEn;

    protected PublicacionJpaEntity() {
    }

    public void actualizarDesde(Publicacion p) {
        this.titulo = p.getTitulo();
        this.descripcion = p.getDescripcion();
        this.textoBusqueda = p.textoBusqueda();
        this.precio = p.getPrecio();
        this.estadoArticulo = p.getEstadoArticulo();
        this.categoria = p.getCategoria();
        this.fotos.clear();
        for (Foto f : p.getFotos()) {
            this.fotos.add(FotoEmbeddable.de(f));
        }
        this.whatsapp = p.getContacto().whatsapp();
        this.correo = p.getContacto().correo();
        this.vendedor = p.getVendedor();
        this.publicadaEn = p.getPublicadaEn();
        this.estado = p.getEstado();
        this.vendidaEn = p.getVendidaEn();
    }

    public Publicacion aDominio() {
        List<Foto> listaFotos = fotos.stream().map(FotoEmbeddable::aDominio).toList();
        return Publicacion.reconstruir(id, titulo, descripcion, precio, estadoArticulo, categoria, listaFotos,
                Contacto.rehidratar(whatsapp, correo), vendedor, publicadaEn, estado, vendidaEn);
    }
}
