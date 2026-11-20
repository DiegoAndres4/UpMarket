package com.upmarket.catalogo.application.service;

import com.upmarket.catalogo.application.port.in.PublicacionUseCase;
import com.upmarket.catalogo.application.port.in.admin.AdminUseCase;
import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin;
import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin.ConteoDia;
import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin.VistaPublicacion;
import com.upmarket.catalogo.application.port.in.admin.FiltroAdminPublicaciones;
import com.upmarket.catalogo.application.port.in.admin.PaginaAdmin;
import com.upmarket.catalogo.application.port.in.admin.PublicacionAdmin;
import com.upmarket.catalogo.application.port.in.admin.UsuarioAdmin;
import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.PagoRepositoryPort;
import com.upmarket.catalogo.application.port.out.PerfilAdmin;
import com.upmarket.catalogo.application.port.out.PerfilAdminPort;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.application.port.out.ResenaRepositoryPort;
import com.upmarket.catalogo.domain.model.Actor;
import com.upmarket.catalogo.domain.model.EstadoPago;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import com.upmarket.catalogo.domain.model.Pago;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.model.Texto;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Panel de administración: estadísticas, moderación de publicaciones y lectura de usuarios.
 * No crea datos ni toca AuthCore; el bloqueo de cuentas queda fuera de este alcance.
 */
public class AdminService implements AdminUseCase {

    private static final int DIAS_DEL_TABLERO = 30;

    private final PublicacionRepositoryPort publicaciones;
    private final PagoRepositoryPort pagos;
    private final ResenaRepositoryPort resenas;
    private final PerfilAdminPort perfiles;
    private final PublicacionUseCase casosDePublicacion;
    private final PoliticaAcceso acceso;
    private final Clock reloj;

    public AdminService(PublicacionRepositoryPort publicaciones, PagoRepositoryPort pagos,
                        ResenaRepositoryPort resenas, PerfilAdminPort perfiles,
                        PublicacionUseCase casosDePublicacion, PoliticaAcceso acceso, Clock reloj) {
        this.publicaciones = publicaciones;
        this.pagos = pagos;
        this.resenas = resenas;
        this.perfiles = perfiles;
        this.casosDePublicacion = casosDePublicacion;
        this.acceso = acceso;
        this.reloj = reloj;
    }

    @Override
    public EstadisticasAdmin estadisticas(Actor actor) {
        acceso.asegurarAdmin(actor, "ver las estadísticas");
        List<Publicacion> todas = todas();

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoPublicacion estado : EstadoPublicacion.values()) {
            porEstado.put(estado.name(), 0L);
        }
        Map<String, Long> porEstadoPago = new LinkedHashMap<>();
        for (EstadoPago estado : EstadoPago.values()) {
            porEstadoPago.put(estado.name(), 0L);
        }
        Map<String, Long> porCategoria = new TreeMap<>();
        long vistasTotales = 0;
        for (Publicacion publicacion : todas) {
            porEstado.merge(publicacion.getEstado().name(), 1L, Long::sum);
            porEstadoPago.merge(publicacion.getEstadoPago().name(), 1L, Long::sum);
            porCategoria.merge(publicacion.getCategoria(), 1L, Long::sum);
            vistasTotales += publicacion.getVistas();
        }

        List<Pago> todosLosPagos = pagos.todos();
        long pagosAprobados = 0;
        BigDecimal ingresos = BigDecimal.ZERO;
        for (Pago pago : todosLosPagos) {
            if (pago.getEstado() == EstadoPago.APROBADO) {
                pagosAprobados++;
                ingresos = ingresos.add(pago.getMonto());
            }
        }

        List<VistaPublicacion> top = todas.stream()
                .sorted(Comparator.comparingLong(Publicacion::getVistas).reversed()
                        .thenComparing(Publicacion::getId))
                .limit(5)
                .map(p -> new VistaPublicacion(p.getId(), p.getTitulo(), p.getVistas()))
                .toList();

        return new EstadisticasAdmin(todas.size(), porEstado, porEstadoPago, pagosAprobados, ingresos,
                vistasTotales, top, porCategoria, publicacionesPorDia(todas), perfiles.contar(),
                resenas.contar());
    }

    @Override
    public PaginaAdmin<PublicacionAdmin> publicaciones(Actor actor, FiltroAdminPublicaciones filtro) {
        acceso.asegurarAdmin(actor, "ver todas las publicaciones");
        String texto = vacio(filtro.texto()) ? null : Texto.normalizar(filtro.texto());
        List<Publicacion> encontradas = publicaciones.buscar(new FiltroPublicacion(texto,
                vacio(filtro.categoria()) ? null : filtro.categoria().trim(), null, null, null,
                filtro.estadoPublicacion(), vacio(filtro.vendedor()) ? null : filtro.vendedor().trim(),
                filtro.estadoPago()));
        return PaginaAdmin.de(encontradas.stream().map(PublicacionAdmin::de).toList(), filtro.pagina(),
                filtro.tamano());
    }

    @Override
    public void eliminarPublicacion(Actor actor, long id) {
        acceso.asegurarAdmin(actor, "eliminar publicaciones");
        casosDePublicacion.eliminar(actor, id);
    }

    @Override
    public PaginaAdmin<UsuarioAdmin> usuarios(Actor actor, int pagina, int tamano) {
        acceso.asegurarAdmin(actor, "ver los usuarios");
        Map<String, Long> publicacionesPorVendedor = new HashMap<>();
        for (Publicacion publicacion : todas()) {
            publicacionesPorVendedor.merge(publicacion.getVendedor(), 1L, Long::sum);
        }
        List<UsuarioAdmin> filas = perfiles.listar().stream()
                .map(perfil -> new UsuarioAdmin(perfil.usuario(), perfil.nombre(), perfil.correo(),
                        perfil.fotoUrl(), perfil.eliminada(),
                        publicacionesPorVendedor.getOrDefault(perfil.usuario(), 0L)))
                .toList();
        return PaginaAdmin.de(filas, pagina, tamano);
    }

    private List<Publicacion> todas() {
        return publicaciones.buscar(new FiltroPublicacion(null, null, null, null, null, null, null, null));
    }

    private List<ConteoDia> publicacionesPorDia(List<Publicacion> todas) {
        LocalDate hoy = LocalDate.now(reloj);
        Map<LocalDate, Long> conteo = new TreeMap<>();
        for (int i = DIAS_DEL_TABLERO - 1; i >= 0; i--) {
            conteo.put(hoy.minusDays(i), 0L);
        }
        for (Publicacion publicacion : todas) {
            LocalDate dia = publicacion.getPublicadaEn().atZone(reloj.getZone()).toLocalDate();
            if (conteo.containsKey(dia)) {
                conteo.merge(dia, 1L, Long::sum);
            }
        }
        return conteo.entrySet().stream().map(fila -> new ConteoDia(fila.getKey().toString(), fila.getValue()))
                .toList();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
