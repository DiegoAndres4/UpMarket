package com.upmarket.catalogo.infrastructure.perfil;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Datos de perfil del usuario. La identidad (correo y contraseña) es de AuthCore; aquí solo se guarda lo
 * que AuthCore no tiene: nombre para mostrar, celular y foto. La clave es el nombre de usuario del token (el correo).
 */
@Entity
@Table(name = "perfil")
public class Perfil {

    @Id
    @Column(length = 120)
    private String usuario;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Column(name = "foto_url", length = 200)
    private String fotoUrl;

    @Column(nullable = false)
    private boolean eliminada;

    protected Perfil() { }

    public Perfil(String usuario, String nombre) {
        this.usuario = usuario;
        this.nombre = nombre;
    }

    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getFotoUrl() { return fotoUrl; }
    public boolean isEliminada() { return eliminada; }

    public void actualizar(String nombre, String telefono, String fotoUrl) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.fotoUrl = fotoUrl;
    }

    /** La cuenta queda marcada como eliminada y sin datos personales. */
    public void marcarEliminada() {
        this.nombre = "Cuenta eliminada";
        this.telefono = null;
        this.fotoUrl = null;
        this.eliminada = true;
    }
}
