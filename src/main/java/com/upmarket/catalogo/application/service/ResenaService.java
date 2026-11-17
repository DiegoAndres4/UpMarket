package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.ListadoResenas;
import com.upmarket.catalogo.application.port.in.ResenaUseCase;
import com.upmarket.catalogo.application.port.in.ResenaVista;
import com.upmarket.catalogo.application.port.in.command.CrearResenaCommand;
import com.upmarket.catalogo.application.port.out.PerfilPublicoPort;
import com.upmarket.catalogo.application.port.out.ResenaRepositoryPort;
import com.upmarket.catalogo.application.port.out.TransaccionPort;
import com.upmarket.catalogo.domain.exception.ResenaDuplicadaException;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.Resena;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Crear una reseña definitiva y consultar las de un vendedor con su promedio. */
public class ResenaService implements ResenaUseCase {

    private final ResenaRepositoryPort resenas;
    private final TransaccionPort transaccion;
    private final Clock reloj;
    private final PerfilPublicoPort perfiles;

    public ResenaService(ResenaRepositoryPort resenas, TransaccionPort transaccion, Clock reloj,
                         PerfilPublicoPort perfiles) {
        this.resenas = resenas;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.perfiles = perfiles;
    }

    @Override
    public ResenaVista crear(Actor actor, String vendedor, CrearResenaCommand comando) {
        if (comando == null || comando.estrellas() == null) {
            throw new ValorInvalidoException(Resena.MENSAJE_ESTRELLAS);
        }
        String destino = vendedor == null ? "" : vendedor.trim();
        Resena nueva = Resena.crear(destino, actor.username(), comando.estrellas(), comando.comentario(),
                reloj.instant());
        Resena guardada = transaccion.ejecutar(() -> {
            if (resenas.existe(destino, actor.username())) {
                throw new ResenaDuplicadaException("Ya calificaste a este vendedor");
            }
            return resenas.guardar(nueva);
        });
        return new ResenaVista(guardada.autor(), nombreDe(guardada.autor()), guardada.estrellas(),
                guardada.comentario(), guardada.creadaEn());
    }

    @Override
    public ListadoResenas listar(String vendedor) {
        String destino = vendedor == null ? "" : vendedor.trim();
        List<Resena> lista = resenas.porVendedor(destino);
        Map<String, String> nombres = perfiles.nombres(lista.stream().map(Resena::autor).distinct().toList());
        List<ResenaVista> vistas = lista.stream()
                .map(r -> new ResenaVista(r.autor(), nombres.getOrDefault(r.autor(), r.autor()),
                        r.estrellas(), r.comentario(), r.creadaEn()))
                .toList();
        double promedio = lista.isEmpty() ? 0.0
                : Math.round(lista.stream().mapToInt(Resena::estrellas).average().orElse(0.0) * 10.0) / 10.0;
        return new ListadoResenas(destino, promedio, lista.size(), vistas);
    }

    private String nombreDe(String usuario) {
        return perfiles.nombres(List.of(usuario)).getOrDefault(usuario, usuario);
    }
}
