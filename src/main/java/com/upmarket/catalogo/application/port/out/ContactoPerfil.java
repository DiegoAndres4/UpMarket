package com.upmarket.catalogo.application.port.out;

/** Nombre y celular de un perfil, sin exponer el usuario ni otros datos. */
public record ContactoPerfil(String nombre, String telefono) {
}
