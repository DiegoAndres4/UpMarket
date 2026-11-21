package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.PagoUseCase;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PasarelaPagoPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.application.port.out.TransaccionPort;
import com.upmarket.catalogo.domain.exception.EstadoInvalidoException;
import com.upmarket.catalogo.domain.exception.PublicacionNoEncontradaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.math.BigDecimal;
import java.time.Clock;

/**
 * Cobro simulado de la tarifa de publicación. El gateway real se conecta detrás de {@link PasarelaPagoPort}.
 */
public class PagoService implements PagoUseCase {

    private final PagoRepositoryPort pagos;
    private final PublicacionRepositoryPort publicaciones;
    private final PasarelaPagoPort pasarela;
    private final TransaccionPort transaccion;
    private final Clock reloj;
    private final PoliticaAcceso acceso;
    private final BigDecimal tarifa;

    public PagoService(PagoRepositoryPort pagos, PublicacionRepositoryPort publicaciones, PasarelaPagoPort pasarela,
                       TransaccionPort transaccion, Clock reloj, PoliticaAcceso acceso, long tarifaCop) {
        if (tarifaCop <= 0) {
            throw new ValorInvalidoException("La tarifa de publicación debe ser mayor que cero");
        }
        this.pagos = pagos;
        this.publicaciones = publicaciones;
        this.pasarela = pasarela;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.acceso = acceso;
        this.tarifa = BigDecimal.valueOf(tarifaCop).setScale(2);
    }

    @Override
    public Pago pagar(Actor actor, long publicacionId) {
        return transaccion.ejecutar(() -> {
            Publicacion publicacion = publicaciones.buscarPorId(publicacionId)
                    .orElseThrow(() -> new PublicacionNoEncontradaException(publicacionId));
            acceso.asegurarEsAutor(publicacion, actor);
            if (publicacion.estaArchivada()) {
                // Una cuenta nueva con el mismo usuario no puede pagar las publicaciones archivadas.
                throw new PublicacionNoEncontradaException(publicacionId);
            }
            if (publicacion.estaPagada() || pagos.buscarPorPublicacion(publicacionId)
                    .map(pago -> pago.getEstado() == com.upmarket.catalogo.domain.model.EstadoPago.APROBADO)
                    .orElse(false)) {
                throw new EstadoInvalidoException("La publicación " + publicacionId + " ya está pagada");
            }
            Pago pago = Pago.pendiente(publicacionId, tarifa, reloj.instant());
            PasarelaPagoPort.ResultadoPago resultado = pasarela.cobrar(tarifa, "PUB-" + publicacionId);
            if (!resultado.aprobado()) {
                pago.rechazar(resultado.referencia(), reloj.instant());
                pagos.guardar(pago);
                throw new EstadoInvalidoException("El pago fue rechazado: " + resultado.referencia());
            }
            pago.aprobar(resultado.referencia(), reloj.instant());
            Pago guardado = pagos.guardar(pago);
            publicacion.activarPago();
            publicaciones.guardar(publicacion);
            return guardado;
        });
    }
}
