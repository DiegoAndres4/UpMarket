package com.upmarket.catalogo.infrastructure.security;

import java.time.Instant;
import java.util.List;

/**
 * Lo que el domain service sabe de quien llama: viene del token firmado por AuthCore, no de una base
 * de datos propia. {@code emitidoEn} es el {@code iat} del token: permite distinguir un token de una
 * cuenta ya eliminada del token de una cuenta nueva con el mismo usuario.
 */
public record UsuarioAutenticado(String username, Long uid, List<String> roles, Instant emitidoEn) { }
