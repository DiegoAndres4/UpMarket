package com.upmarket.catalogo.application;

import com.upmarket.catalogo.application.port.in.command.*;
import com.upmarket.catalogo.application.service.*;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.infrastructure.adapters.out.payment.PasarelaPagoSimulada;
import com.upmarket.catalogo.testsupport.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** Arma los servicios con dobles en memoria: así se prueba la aplicación sin Spring ni base de datos. */
final class Escenario {

    final PublicacionRepositoryEnMemoria publicaciones = new PublicacionRepositoryEnMemoria();
    final CategoriaRepositoryEnMemoria categorias = new CategoriaRepositoryEnMemoria();
    final TransaccionDirecta tx = new TransaccionDirecta();
    final RelojAjustable reloj = Datos.relojAjustable();
    final PoliticaAcceso acceso = new PoliticaAcceso();

    final PagoRepositoryEnMemoria pagos = new PagoRepositoryEnMemoria();
    final PasarelaPagoSimulada pasarela = new PasarelaPagoSimulada();
    final PasarelaPaypalFalsa paypal = new PasarelaPaypalFalsa();

    final CategoriaService categoriaService = new CategoriaService(categorias, publicaciones, tx, acceso);
    final PublicacionService publicacionService = new PublicacionService(publicaciones, categorias, tx, reloj, acceso);
    final PagoService pagoService = new PagoService(pagos, publicaciones, pasarela, paypal, tx, reloj, acceso,
            3000, "simulado", "", new BigDecimal("0.75"), true);
    final PagoService pagoServicePaypal = new PagoService(pagos, publicaciones, pasarela, paypal, tx, reloj, acceso,
            3000, "paypal", "cliente-publico", new BigDecimal("0.75"), true);

    Escenario() {
        categoriaService.asegurarIniciales();
    }

    PublicarCommand comando(String titulo, String descripcion, String precio, String estado, String categoria) {
        return new PublicarCommand(titulo, descripcion, precio, estado, categoria,
                List.of(new FotoComando("https://archivos.example/f1.jpg", "image/jpeg", 200_000)), "3001234567", "maria@correo.com");
    }

    /** Publica y aprueba el pago: el escenario heredado sigue viendo la publicación en el catálogo. */
    Publicacion publicar(String vendedor, String titulo, String descripcion, String precio, String categoria) {
        return publicar(vendedor, titulo, descripcion, precio, "usado", categoria);
    }

    Publicacion publicar(String vendedor, String titulo, String descripcion, String precio, String estado,
                         String categoria) {
        Publicacion p = publicarPendiente(vendedor, titulo, descripcion, precio, estado, categoria);
        p.activarPago();
        return publicaciones.guardar(p);
    }

    Publicacion publicarPendiente(String vendedor, String titulo, String descripcion, String precio, String categoria) {
        return publicarPendiente(vendedor, titulo, descripcion, precio, "usado", categoria);
    }

    Publicacion publicarPendiente(String vendedor, String titulo, String descripcion, String precio, String estado,
                                  String categoria) {
        return publicacionService.publicar(Datos.estudiante(vendedor),
                comando(titulo, descripcion, precio, estado, categoria));
    }

    Pago pagar(String vendedor, long idPublicacion) {
        return pagoService.pagar(Datos.estudiante(vendedor), idPublicacion);
    }

    void pasar(Duration d) {
        reloj.avanzar(d);
    }

    Actor admin() { return Datos.admin(); }
}
