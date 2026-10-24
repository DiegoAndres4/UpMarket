package com.upmarket.catalogo.application.port.in.command;

/** Datos de una foto ya subida al almacenamiento de archivos. */
public record FotoComando(String url, String tipo, long tamanoBytes) { }
