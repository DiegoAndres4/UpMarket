package com.upmarket.catalogo.infrastructure.config;

import com.upmarket.catalogo.application.port.in.CategoriaUseCase;
import com.upmarket.catalogo.application.port.out.CategoriaRepositoryPort;
import com.upmarket.catalogo.application.port.out.FiltroPublicacion;
import com.upmarket.catalogo.application.port.out.PublicacionRepositoryPort;
import com.upmarket.catalogo.domain.model.Categoria;
import com.upmarket.catalogo.domain.model.Contacto;
import com.upmarket.catalogo.domain.model.EstadoArticulo;
import com.upmarket.catalogo.domain.model.Foto;
import com.upmarket.catalogo.domain.model.Publicacion;
import com.upmarket.catalogo.domain.model.Texto;
import com.upmarket.catalogo.infrastructure.perfil.Perfil;
import com.upmarket.catalogo.infrastructure.perfil.PerfilRepository;
import com.upmarket.catalogo.infrastructure.storage.AlmacenFotosLocal;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "upmarket.seed", name = "enabled", havingValue = "true")
public class DatosDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoSeeder.class);

    private static final Map<String, PerfilDemo> PERFILES = new LinkedHashMap<>();

    static {
        PERFILES.put("admin", new PerfilDemo("Administrador UpMarket", "3001234567", "admin@upmarket.local"));
        PERFILES.put("laura.gomez@demo.upmarket",
                new PerfilDemo("Laura Gómez", "3102345678", "laura.gomez@demo.upmarket"));
        PERFILES.put("carlos.ruiz@demo.upmarket",
                new PerfilDemo("Carlos Ruiz", "3203456789", "carlos.ruiz@demo.upmarket"));
        PERFILES.put("andrea.torres@demo.upmarket",
                new PerfilDemo("Andrea Torres", "3154567890", "andrea.torres@demo.upmarket"));
    }

    private static final List<Semilla> SEMILLAS = List.of(
            new Semilla("admin", "Tecnología", "MacBook Air M1 2020",
                    "MacBook Air M1 de 2020, 8 GB de RAM y 256 GB SSD. Incluye cargador original y funda.",
                    "2400000", EstadoArticulo.USADO, false, 2),
            new Semilla("laura.gomez@demo.upmarket", "Tecnología", "Audífonos Sony WH-1000XM4",
                    "Audífonos Sony WH-1000XM4 con estuche y cable. Cancelación de ruido activa.",
                    "650000", EstadoArticulo.USADO, false, 4),
            new Semilla("carlos.ruiz@demo.upmarket", "Tecnología", "Monitor LG de 24 pulgadas",
                    "Monitor LG de 24 pulgadas, Full HD, HDMI y VGA. Incluye base y cable.",
                    "480000", EstadoArticulo.USADO, false, 6),
            new Semilla("andrea.torres@demo.upmarket", "Útiles académicos", "Mochila Targus para portátil",
                    "Mochila Targus para portátil de hasta 15 pulgadas, con compartimento acolchado.",
                    "110000", EstadoArticulo.USADO, false, 8),
            new Semilla("andrea.torres@demo.upmarket", "Alimentos", "Café de origen 500 g",
                    "Café de origen colombiano, 500 gramos, tostión media. Recién molido o en grano.",
                    "38000", EstadoArticulo.NUEVO, false, 10),
            new Semilla("carlos.ruiz@demo.upmarket", "Libros", "Clean Code (edición en español)",
                    "Clean Code de Robert C. Martin, edición en español. Ideal para programadores.",
                    "85000", EstadoArticulo.USADO, false, 12),
            new Semilla("laura.gomez@demo.upmarket", "Libros", "Cálculo de Stewart 8ª edición",
                    "Libro Cálculo de James Stewart, octava edición. En buen estado, sin subrayados.",
                    "120000", EstadoArticulo.USADO, false, 18),
            new Semilla("laura.gomez@demo.upmarket", "Otros", "Bicicleta GW de montaña",
                    "Bicicleta GW de montaña, rin 29, frenos de disco. Recién ajustada.",
                    "750000", EstadoArticulo.USADO, false, 24),
            new Semilla("admin", "Útiles académicos", "Calculadora Casio fx-991LA X",
                    "Calculadora científica Casio fx-991LA X, con tapa y manual. Funciona perfectamente.",
                    "95000", EstadoArticulo.USADO, false, 30),
            new Semilla("andrea.torres@demo.upmarket", "Servicios", "Mantenimiento de PC y portátiles",
                    "Mantenimiento preventivo y correctivo de PC y portátiles. Incluye limpieza y diagnóstico.",
                    "60000", EstadoArticulo.NUEVO, false, 36),
            new Semilla("admin", "Servicios", "Clases de cálculo diferencial",
                    "Clases personalizadas de cálculo diferencial para estudiantes universitarios. Primera sesión de prueba.",
                    "45000", EstadoArticulo.NUEVO, false, 48),
            new Semilla("admin", "Otros", "Silla ergonómica de oficina",
                    "Silla ergonómica de oficina con soporte lumbar y brazos ajustables. Vendida.",
                    "320000", EstadoArticulo.USADO, true, 72)
    );

    private final CategoriaUseCase categorias;
    private final CategoriaRepositoryPort categoriaRepository;
    private final PublicacionRepositoryPort publicaciones;
    private final PerfilRepository perfiles;
    private final AlmacenFotosLocal almacen;
    private final Clock reloj;

    public DatosDemoSeeder(CategoriaUseCase categorias, CategoriaRepositoryPort categoriaRepository,
                           PublicacionRepositoryPort publicaciones, PerfilRepository perfiles,
                           AlmacenFotosLocal almacen, Clock reloj) {
        this.categorias = categorias;
        this.categoriaRepository = categoriaRepository;
        this.publicaciones = publicaciones;
        this.perfiles = perfiles;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.setProperty("java.awt.headless", "true");
        int categoriasCreadas = categorias.asegurarIniciales();
        int perfilesCreados = sembrarPerfiles();
        Resumen resumen = sembrarPublicaciones();
        log.info("Seed demo: {} categorías nuevas, {} perfiles nuevos, {} publicaciones nuevas, {} omitidas",
                categoriasCreadas, perfilesCreados, resumen.creadas(), resumen.omitidas());
    }

    private int sembrarPerfiles() {
        int creados = 0;
        for (Map.Entry<String, PerfilDemo> entrada : PERFILES.entrySet()) {
            if (perfiles.existsById(entrada.getKey())) {
                continue;
            }
            PerfilDemo demo = entrada.getValue();
            Perfil perfil = new Perfil(entrada.getKey(), demo.nombre());
            perfil.actualizar(demo.nombre(), demo.telefono(), null);
            perfiles.save(perfil);
            creados++;
        }
        return creados;
    }

    private Resumen sembrarPublicaciones() {
        Map<String, Set<String>> titulosExistentes = new HashMap<>();
        for (String vendedor : PERFILES.keySet()) {
            Set<String> titulos = publicaciones.buscar(filtroDe(vendedor)).stream()
                    .map(publicacion -> Texto.normalizar(publicacion.getTitulo()))
                    .collect(Collectors.toCollection(HashSet::new));
            titulosExistentes.put(vendedor, titulos);
        }

        int creadas = 0;
        int omitidas = 0;
        for (Semilla semilla : SEMILLAS) {
            Set<String> titulos = titulosExistentes.get(semilla.vendedor());
            if (!titulos.add(Texto.normalizar(semilla.titulo()))) {
                omitidas++;
                continue;
            }

            PerfilDemo demo = PERFILES.get(semilla.vendedor());
            Contacto contacto = new Contacto(demo.telefono(), demo.correo());
            Instant publicadaEn = reloj.instant().minus(Duration.ofHours(semilla.horasAtras()));
            Publicacion publicacion = Publicacion.publicar(semilla.titulo(), semilla.descripcion(),
                    semilla.precio(), semilla.estadoArticulo(), categoria(semilla.categoria()),
                    foto(semilla.categoria()), contacto, semilla.vendedor(), publicadaEn);
            if (semilla.vendida()) {
                publicacion.marcarVendida(publicadaEn.plus(Duration.ofHours(2)));
            }
            publicaciones.guardar(publicacion);
            creadas++;
        }
        return new Resumen(creadas, omitidas);
    }

    private Categoria categoria(String nombre) {
        return categoriaRepository.buscarPorClave(Categoria.claveDe(nombre))
                .orElseThrow(() -> new IllegalStateException("Falta la categoría inicial: " + nombre));
    }

    private List<Foto> foto(String categoria) {
        try {
            byte[] contenido = png(colorDe(categoria));
            AlmacenFotosLocal.Guardada guardada = almacen.guardar(contenido);
            return List.of(new Foto("/api/fotos/" + guardada.nombre(), guardada.tipo(), guardada.tamanoBytes()));
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo generar la foto de ejemplo para {}: {}", categoria, e.getMessage());
            return new ArrayList<>();
        }
    }

    private static FiltroPublicacion filtroDe(String vendedor) {
        return new FiltroPublicacion(null, null, null, null, null, null, vendedor);
    }

    private static int colorDe(String categoria) {
        return switch (categoria) {
            case "Tecnología" -> 0x2563EB;
            case "Libros" -> 0x7C3AED;
            case "Útiles académicos" -> 0x059669;
            case "Alimentos" -> 0xD97706;
            case "Servicios" -> 0x0891B2;
            default -> 0xDB2777;
        };
    }

    private static byte[] png(int rgb) throws IOException {
        int ancho = 640;
        int alto = 480;
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Color base = new Color(rgb);
        Color claro = base.brighter();
        for (int y = 0; y < alto; y++) {
            int fondo = mezclar(base, claro, (double) y / (alto - 1));
            for (int x = 0; x < ancho; x++) {
                int dx = x - ancho / 2;
                int dy = y - alto / 2;
                int color = dx * dx + dy * dy <= 130 * 130 ? mezclar(claro, Color.WHITE, 0.25) : fondo;
                imagen.setRGB(x, y, color);
            }
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        if (!ImageIO.write(imagen, "png", salida)) {
            throw new IOException("No hay escritor PNG disponible");
        }
        return salida.toByteArray();
    }

    private static int mezclar(Color a, Color b, double t) {
        int rojo = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int verde = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int azul = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(rojo, verde, azul).getRGB();
    }

    private record PerfilDemo(String nombre, String telefono, String correo) { }

    private record Semilla(String vendedor, String categoria, String titulo, String descripcion, String precio,
                           EstadoArticulo estadoArticulo, boolean vendida, long horasAtras) { }

    private record Resumen(int creadas, int omitidas) { }
}
