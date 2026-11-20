package com.upmarket.catalogo.infrastructure.adapters.in.rest;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.command.BuscarQuery;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.infrastructure.adapters.in.rest.dto.AvisoPublicoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * Lo que un visitante puede ver sin iniciar sesión: la cartelera y las categorías.
 * Reutiliza los mismos casos de uso que la búsqueda autenticada; aquí solo se recorta la respuesta
 * a los campos públicos y se aplica el límite de la landing.
 */
@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    private static final int LIMITE_POR_DEFECTO = 12;
    private static final int LIMITE_MAXIMO = 24;
    private static final int TEXTO_MAXIMO = 80;

    private final PublicacionUseCase publicaciones;
    private final CategoriaUseCase categorias;

    public PublicoController(PublicacionUseCase publicaciones, CategoriaUseCase categorias) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
    }

    @GetMapping("/cartelera")
    public List<AvisoPublicoResponse> cartelera(@RequestParam(required = false) String texto,
                                                @RequestParam(required = false) String categoria,
                                                @RequestParam(defaultValue = "12") int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ValorInvalidoException("El límite debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        if (texto != null && texto.length() > TEXTO_MAXIMO) {
            throw new ValorInvalidoException("El texto de búsqueda no puede superar los " + TEXTO_MAXIMO + " caracteres");
        }
        return publicaciones.buscar(new BuscarQuery(texto, categoria, null, null, null)).stream()
                .limit(limite)
                .map(AvisoPublicoResponse::de)
                .toList();
    }

    @GetMapping("/categorias")
    public List<String> categorias() {
        return categorias.listar().stream().map(com.upmarket.catalogo.application.port.in.CategoriaResumen::nombre).toList();
    }
}
