package com.upmarket.catalogo.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlmacenDocumentosCloudinaryTest {

    private static final byte[] PDF = "%PDF-1.7".getBytes();
    private static final String NOMBRE = "upmarket-recibo-REC-000001.pdf";
    private static final String PUBLIC_ID = "upmarket/recibos/upmarket-recibo-REC-000001.pdf";

    private ClienteDocumentosCloudinary cliente;
    private AlmacenDocumentosCloudinary almacen;

    @BeforeEach
    void preparar() {
        cliente = mock(ClienteDocumentosCloudinary.class);
        almacen = new AlmacenDocumentosCloudinary(cliente);
    }

    @Test
    void subeComoRawPrivadoConLaCarpetaYElNombreOriginal() throws Exception {
        Map<?, ?> respuesta = Map.of("public_id", PUBLIC_ID, "bytes", PDF.length);
        when(cliente.subir(any(byte[].class), anyMap())).thenAnswer(invocacion -> respuesta);

        DocumentoAlmacenado documento = almacen.guardar(PDF, NOMBRE, AlmacenDocumentosPort.CARPETA_RECIBOS,
                "application/pdf");

        assertThat(documento.proveedor()).isEqualTo("cloudinary");
        assertThat(documento.referencia()).isEqualTo(PUBLIC_ID);
        assertThat(documento.nombreArchivo()).isEqualTo(NOMBRE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> opciones = ArgumentCaptor.forClass(Map.class);
        verify(cliente).subir(eq(PDF), opciones.capture());
        assertThat(opciones.getValue()).containsEntry("resource_type", "raw")
                .containsEntry("type", "private")
                .containsEntry("folder", AlmacenDocumentosPort.CARPETA_RECIBOS)
                .containsEntry("use_filename", true)
                .containsEntry("unique_filename", true)
                .containsEntry("filename_override", NOMBRE);
    }

    @Test
    void firmaElEnlaceConCaducidadYSinUrlPublicaPermanente() throws Exception {
        Map<?, ?> respuesta = Map.of("public_id", PUBLIC_ID, "bytes", PDF.length);
        when(cliente.subir(any(byte[].class), anyMap())).thenAnswer(invocacion -> respuesta);
        when(cliente.enlacePrivado(eq(PUBLIC_ID), eq(""), anyMap()))
                .thenReturn("https://res.cloudinary.com/demo/raw/private/s--firma--/recibo.pdf?expires_at=1");
        DocumentoAlmacenado documento = almacen.guardar(PDF, NOMBRE, AlmacenDocumentosPort.CARPETA_RECIBOS,
                "application/pdf");

        long antes = Instant.now().plus(Duration.ofMinutes(9)).getEpochSecond();
        URI enlace = almacen.enlaceDescarga(documento, Duration.ofMinutes(10));

        assertThat(enlace.toString()).contains("res.cloudinary.com").contains("private");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> opciones = ArgumentCaptor.forClass(Map.class);
        verify(cliente).enlacePrivado(eq(PUBLIC_ID), eq(""), opciones.capture());
        long expira = ((Number) opciones.getValue().get("expires_at")).longValue();
        assertThat(expira).isBetween(antes, Instant.now().plus(Duration.ofMinutes(11)).getEpochSecond());
        assertThat(opciones.getValue()).containsEntry("resource_type", "raw")
                .containsEntry("type", "private")
                .containsEntry("attachment", true);
    }
}
