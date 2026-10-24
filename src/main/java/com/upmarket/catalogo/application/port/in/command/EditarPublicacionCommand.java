package com.upmarket.catalogo.application.port.in.command;

public record EditarPublicacionCommand(long id, String titulo, String descripcion, String precio, String estado, String categoria) { }
