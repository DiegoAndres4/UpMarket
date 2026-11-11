package com.upmarket.catalogo.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.service.CategoriaService;
import com.upmarket.catalogo.domain.model.EstadoPublicacion;
import com.upmarket.catalogo.domain.service.PoliticaAcceso;
import com.upmarket.catalogo.infrastructure.perfil.Perfil;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import com.upmarket.catalogo.testsupport.CategoriaRepositoryEnMemoria;
import com.upmarket.catalogo.testsupport.Datos;
import com.upmarket.catalogo.testsupport.PublicacionRepositoryEnMemoria;
import com.upmarket.catalogo.testsupport.TransaccionDirecta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatosDemoSeederTest {

    @TempDir
    Path fotos;

    @Test
    @DisplayName("El seed crea perfiles y publicaciones una sola vez")
    void idempotente() throws Exception {
        CategoriaRepositoryEnMemoria categorias = new CategoriaRepositoryEnMemoria();
        PublicacionRepositoryEnMemoria publicaciones = new PublicacionRepositoryEnMemoria();
        CategoriaService categoriaService = new CategoriaService(categorias, publicaciones,
                new TransaccionDirecta(), new PoliticaAcceso());

        Set<String> perfilesGuardados = new HashSet<>();
        PerfilRepository perfiles = mock(PerfilRepository.class);
        when(perfiles.existsById(anyString())).thenAnswer(inv -> perfilesGuardados.contains(inv.getArgument(0)));
        when(perfiles.save(any(Perfil.class))).thenAnswer(inv -> {
            Perfil perfil = inv.getArgument(0);
            perfilesGuardados.add(perfil.getUsuario());
            return perfil;
        });

        DatosDemoSeeder seeder = new DatosDemoSeeder(categoriaService, categorias, publicaciones, perfiles,
                new AlmacenFotosLocal(fotos.toString()), Datos.relojFijo());

        seeder.run(null);

        assertEquals(6, categorias.listar().size());
        assertEquals(4, perfilesGuardados.size());
        assertEquals(12, publicaciones.buscar(filtro()).size());
        assertEquals(1, publicaciones.buscar(filtro()).stream()
                .filter(p -> p.getEstado() == EstadoPublicacion.VENDIDA).count());
        try (var archivos = Files.list(fotos)) {
            assertEquals(12, archivos.count());
        }

        seeder.run(null);

        assertEquals(12, publicaciones.buscar(filtro()).size());
        try (var archivos = Files.list(fotos)) {
            assertEquals(12, archivos.count());
        }
    }

    private static FiltroPublicacion filtro() {
        return new FiltroPublicacion(null, null, null, null, null, null, null);
    }
}
