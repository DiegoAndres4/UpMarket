package com.upmarket.catalogo.application;

import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.application.service.*;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.testsupport.*;
import java.time.Duration;
import java.util.List;

/** Arma los servicios con dobles en memoria: así se prueba la aplicación sin Spring ni base de datos. */
final class Escenario {

    final PublicacionRepositoryEnMemoria publicaciones = new PublicacionRepositoryEnMemoria();
    final CategoriaRepositoryEnMemoria categorias = new CategoriaRepositoryEnMemoria();
    final TransaccionDirecta tx = new TransaccionDirecta();
    final RelojAjustable reloj = Datos.relojAjustable();
    final PoliticaAcceso acceso = new PoliticaAcceso();

    final CategoriaService categoriaService = new CategoriaService(categorias, publicaciones, tx, acceso);
    final PublicacionService publicacionService = new PublicacionService(publicaciones, categorias, tx, reloj, acceso);

    Escenario() {
        categoriaService.asegurarIniciales();
    }

    PublicarCommand comando(String titulo, String descripcion, String precio, String estado, String categoria) {
        return new PublicarCommand(titulo, descripcion, precio, estado, categoria,
                List.of(new FotoComando("https://archivos.example/f1.jpg", "image/jpeg", 200_000)), "3001234567", "maria@correo.com");
    }

    Publicacion publicar(String vendedor, String titulo, String descripcion, String precio, String categoria) {
        return publicacionService.publicar(Datos.estudiante(vendedor), comando(titulo, descripcion, precio, "usado", categoria));
    }

    void pasar(Duration d) {
        reloj.avanzar(d);
    }

    Actor admin() { return Datos.admin(); }
}
