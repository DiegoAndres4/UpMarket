package com.upmarket.catalogo.infrastructure.perfil;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Copia del perfil de una cuenta eliminada. La fila activa de {@code perfil} se borra para liberar
 * el usuario, pero el registro no se pierde: queda aquí con los datos que tenía al darse de baja.
 */
@Entity
@Table(name = "perfiles_eliminados")
public class PerfilEliminado {

    public static final String MOTIVO_BAJA_USUARIO = "BAJA_USUARIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String correo;

    @Column(name = "foto_url", length = 200)
    private String fotoUrl;

    @Column(name = "eliminado_en", nullable = false)
    private Instant eliminadoEn;

    @Column(nullable = false, length = 30)
    private String motivo;

    protected PerfilEliminado() { }

    public PerfilEliminado(String usuario, String nombre, String telefono, String correo, String fotoUrl,
                           Instant eliminadoEn, String motivo) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.telefono = telefono;
        this.correo = correo;
        this.fotoUrl = fotoUrl;
        this.eliminadoEn = eliminadoEn;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getCorreo() { return correo; }
    public String getFotoUrl() { return fotoUrl; }
    public Instant getEliminadoEn() { return eliminadoEn; }
    public String getMotivo() { return motivo; }
}
