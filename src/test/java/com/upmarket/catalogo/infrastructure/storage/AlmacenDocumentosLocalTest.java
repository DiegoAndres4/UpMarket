package com.upmarket.catalogo.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.upmarket.catalogo.application.port.out.AlmacenDocumentosPort;
import com.upmarket.catalogo.application.port.out.DocumentoAlmacenado;
import com.upmarket.catalogo.domain.exception.DocumentoNoEncontradoException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlmacenDocumentosLocalTest {

    private static final byte[] PDF = "%PDF-1.7 contenido de prueba".getBytes();

    @TempDir
    Path directorio;

    @Test
    void guardaBajoUnNombreGeneradoYDevuelveElMismoContenido() {
        AlmacenDocumentosLocal almacen = new AlmacenDocumentosLocal(directorio.toString());

        DocumentoAlmacenado documento = almacen.guardar(PDF, "upmarket-recibo-REC-000001.pdf",
                AlmacenDocumentosPort.CARPETA_RECIBOS, "application/pdf");

        assertThat(documento.proveedor()).isEqualTo("local");
        assertThat(documento.referencia()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(documento.nombreArchivo()).isEqualTo("upmarket-recibo-REC-000001.pdf");
        assertThat(documento.tamanoBytes()).isEqualTo(PDF.length);
        assertThat(Files.isRegularFile(directorio.resolve(documento.referencia()))).isTrue();
        assertThat(almacen.leer(documento)).isEqualTo(PDF);
    }

    @Test
    void usaLaExtensionXmlParaLosReportes() {
        AlmacenDocumentosLocal almacen = new AlmacenDocumentosLocal(directorio.toString());

        DocumentoAlmacenado documento = almacen.guardar("<reporte/>".getBytes(), "reporte.xml",
                AlmacenDocumentosPort.CARPETA_REPORTES, "application/xml");

        assertThat(documento.referencia()).endsWith(".xml");
    }

    @Test
    void noGeneraEnlacesRemotosPorqueSirvePorStreaming() {
        AlmacenDocumentosLocal almacen = new AlmacenDocumentosLocal(directorio.toString());

        DocumentoAlmacenado documento = almacen.guardar(PDF, "recibo.pdf", AlmacenDocumentosPort.CARPETA_RECIBOS,
                "application/pdf");

        assertThat(almacen.enlaceDescarga(documento, Duration.ofMinutes(10))).isNull();
    }

    @Test
    void eliminaElArchivoYRepetirNoFalla() {
        AlmacenDocumentosLocal almacen = new AlmacenDocumentosLocal(directorio.toString());
        DocumentoAlmacenado documento = almacen.guardar(PDF, "reporte.pdf",
                AlmacenDocumentosPort.CARPETA_REPORTES, "application/pdf");

        almacen.eliminar(documento);
        almacen.eliminar(documento);

        assertThat(Files.exists(directorio.resolve(documento.referencia()))).isFalse();
    }

    @Test
    void rechazaReferenciasQueNoGeneroElServidor() {
        AlmacenDocumentosLocal almacen = new AlmacenDocumentosLocal(directorio.toString());
        DocumentoAlmacenado ajeno = new DocumentoAlmacenado("local", "../recibo.pdf", "recibo.pdf",
                "application/pdf", 10);

        assertThatThrownBy(() -> almacen.leer(ajeno)).isInstanceOf(DocumentoNoEncontradoException.class);
    }
}
