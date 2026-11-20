package com.upmarket.catalogo.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.upmarket.catalogo.application.port.out.AlmacenFotosPort;
import com.upmarket.catalogo.domain.exception.ValorInvalidoException;
import com.upmarket.catalogo.domain.model.Foto;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlmacenFotosCloudinaryTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] NO_IMAGEN = {1, 2, 3};

    private Cloudinary cloudinary;
    private Uploader uploader;
    private AlmacenFotosCloudinary almacen;

    @BeforeEach
    void preparar() throws Exception {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        almacen = new AlmacenFotosCloudinary(cloudinary);
    }

    @Test
    void subeLaImagenALaCarpetaYDevuelveSuUrlSegura() throws Exception {
        String url = "https://res.cloudinary.com/demo/image/upload/v1/upmarket/publicaciones/foto.png";
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", url, "format", "png", "bytes", 12));

        Foto foto = almacen.guardar(PNG, AlmacenFotosPort.CARPETA_PUBLICACIONES);

        assertThat(foto.url()).isEqualTo(url);
        assertThat(foto.tipo()).isEqualTo("image/png");
        assertThat(foto.tamanoBytes()).isEqualTo(12);
        verify(uploader).upload(eq(PNG), argThat(opciones ->
                AlmacenFotosPort.CARPETA_PUBLICACIONES.equals(((Map<?, ?>) opciones).get("folder"))));
    }

    @Test
    void usaLaCarpetaDePerfilesCuandoSeLeIndica() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/perfil.png"));

        almacen.guardar(PNG, AlmacenFotosPort.CARPETA_PERFILES);

        verify(uploader).upload(eq(PNG), argThat(opciones ->
                AlmacenFotosPort.CARPETA_PERFILES.equals(((Map<?, ?>) opciones).get("folder"))));
    }

    @Test
    void rechazaContenidoQueNoEsJpgNiPngSinLlamarAlCdn() throws Exception {
        assertThatThrownBy(() -> almacen.guardar(NO_IMAGEN, AlmacenFotosPort.CARPETA_PUBLICACIONES))
                .isInstanceOf(ValorInvalidoException.class);

        verify(cloudinary, never()).uploader();
    }

    @Test
    void borraUsandoElIdPublicoDerivadoDeLaUrl() throws Exception {
        almacen.borrar("https://res.cloudinary.com/demo/image/upload/v1699999999/upmarket/perfiles/abc123.png");

        verify(uploader).destroy(eq("upmarket/perfiles/abc123"), anyMap());
    }

    @Test
    void noIntentaBorrarEnElCdnUnaUrlQueNoEsDeCloudinary() throws Exception {
        almacen.borrar("/api/fotos/00000000-0000-0000-0000-000000000000.jpg");

        verify(cloudinary, never()).uploader();
    }
}
