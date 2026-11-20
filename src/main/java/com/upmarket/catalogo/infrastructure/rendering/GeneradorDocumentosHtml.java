package com.upmarket.catalogo.infrastructure.rendering;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.upmarket.catalogo.application.port.out.DatosRecibo;
import com.upmarket.catalogo.application.port.out.GeneradorDocumentosPort;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Dibuja los documentos con HTML/CSS y openhtmltopdf, para que compartan la identidad de la app.
 * Toda la información del usuario se escapa antes de entrar al HTML. Si las fuentes Archivo no
 * están en el classpath, cae a Helvetica sin bloquear la generación.
 */
@Component
public class GeneradorDocumentosHtml implements GeneradorDocumentosPort {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES_CO).withZone(BOGOTA);

    private static final String REGULAR = "/fonts/Archivo-Regular.ttf";
    private static final String BOLD = "/fonts/Archivo-Bold.ttf";

    private final boolean conArchivo;
    private final String familia;
    private final String familiaNegrita;

    public GeneradorDocumentosHtml() {
        this.conArchivo = recurso(REGULAR) != null && recurso(BOLD) != null;
        this.familia = conArchivo ? "Archivo" : "Helvetica";
        this.familiaNegrita = conArchivo ? "ArchivoNegrita" : "Helvetica";
    }

    @Override
    public byte[] reciboPago(DatosRecibo datos) {
        String html = recibo(datos);
        return pdf(html);
    }

    private String recibo(DatosRecibo datos) {
        String monto = dinero(datos.monto());
        String emitido = FECHA_HORA.format(datos.generadoEn());
        String aprobado = datos.aprobadoEn() == null ? "—" : FECHA_HORA.format(datos.aprobadoEn());
        return """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="utf-8" /><style>
                @page { size: A4 portrait; margin: 18mm; }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: '%s', Helvetica, Arial, sans-serif; color: #1A1A1A;
                       font-size: 10.5pt; line-height: 1.45; }
                .negrita { font-family: '%s', Helvetica, Arial, sans-serif; font-weight: 700; }
                .cabecera { width: 100%%; border-collapse: collapse; }
                .cabecera td { vertical-align: top; }
                .marca { font-size: 27pt; letter-spacing: -0.045em; line-height: 1; }
                .marca .rojo { color: #D62828; }
                .marca .tinta { color: #1A1A1A; }
                .meta { text-align: right; font-size: 9.5pt; color: #4A4A4A; }
                .meta .titulo { font-size: 13pt; color: #1A1A1A; }
                .banda { width: 100%%; border-collapse: collapse; margin-top: 12mm; background: #F5B700; }
                .banda td { vertical-align: middle; padding: 7mm 8mm; }
                .monto { font-size: 26pt; letter-spacing: -0.03em; }
                .pagado { text-align: right; }
                .pagado .estado { font-size: 12pt; }
                .columnas { width: 100%%; border-collapse: collapse; margin-top: 10mm; }
                .columnas td { width: 50%%; vertical-align: top; padding-right: 8mm; }
                .seccion { font-size: 9pt; color: #4A4A4A; margin-bottom: 2mm; }
                .nombre { font-size: 11.5pt; }
                .detalle { width: 100%%; border-collapse: collapse; margin-top: 11mm; }
                .detalle th { background: #F2EDE3; color: #1A1A1A; text-align: left;
                              padding: 3.2mm 3mm; font-size: 9.5pt; font-weight: 700; }
                .detalle td { padding: 3.4mm 3mm; border-bottom: 0.4pt solid #E5E0D5; }
                .derecha { text-align: right; }
                .datos { margin-top: 8mm; background: #FAF7F0; padding: 5mm 6mm; }
                .datos div { margin-bottom: 1.2mm; }
                .nota { margin-top: 8mm; font-size: 9pt; color: #4A4A4A;
                        border-left: 2.5pt solid #D62828; padding-left: 3mm; }
                .pie { margin-top: 10mm; border-top: 0.5pt solid #E5E0D5; padding-top: 2.5mm;
                       font-size: 8.5pt; color: #4A4A4A; }
                .pie table { width: 100%%; border-collapse: collapse; }
                </style></head><body>
                <table class="cabecera"><tr>
                  <td>
                    <div class="marca negrita"><span class="tinta">UP</span><span class="rojo">Market</span></div>
                    <div style="margin-top:2mm; color:#4A4A4A; font-size:9pt;">
                      Universidad de Pamplona · Villa del Rosario
                    </div>
                  </td>
                  <td class="meta">
                    <div class="titulo negrita">Recibo de pago</div>
                    <div style="margin-top:1.5mm;"><span class="negrita">N.º</span> %s</div>
                    <div><span class="negrita">Emitido el</span> %s</div>
                  </td>
                </tr></table>

                <table class="banda"><tr>
                  <td><div class="monto negrita">%s</div>
                      <div style="margin-top:1mm;">Tarifa de publicación</div></td>
                  <td class="pagado"><div class="estado negrita">Pagado</div>
                      <div style="margin-top:1mm;">Pago simulado</div></td>
                </tr></table>

                <table class="columnas"><tr>
                  <td>
                    <div class="seccion">Emitido por</div>
                    <div class="nombre negrita">UPMarket</div>
                    <div>Proyecto Integrador</div>
                    <div>Universidad de Pamplona, sede Villa del Rosario</div>
                  </td>
                  <td>
                    <div class="seccion">Pagado por</div>
                    <div class="nombre negrita">%s</div>
                    <div>%s</div>
                  </td>
                </tr></table>

                <table class="detalle">
                  <thead><tr>
                    <th>Concepto</th><th class="derecha">Cantidad</th>
                    <th class="derecha">Valor</th><th class="derecha">Total</th>
                  </tr></thead>
                  <tbody><tr>
                    <td>Tarifa de publicación — «%s»</td>
                    <td class="derecha">1</td>
                    <td class="derecha">%s</td>
                    <td class="derecha negrita">%s</td>
                  </tr></tbody>
                </table>

                <div class="datos">
                  <div><span class="negrita">Referencia:</span> %s</div>
                  <div><span class="negrita">Fecha de aprobación:</span> %s</div>
                  <div><span class="negrita">Medio de pago:</span> Pasarela simulada</div>
                </div>

                <div class="nota">Pago simulado — este documento no tiene validez fiscal ante la DIAN.</div>

                <div class="pie"><table><tr>
                  <td>Generado por UPMarket el %s</td>
                  <td style="text-align:right;">Página 1 de 1</td>
                </tr></table></div>
                </body></html>
                """.formatted(familia, familiaNegrita, escapar(datos.numero()), escapar(emitido), escapar(monto),
                escapar(datos.compradorNombre()), escapar(datos.compradorCorreo()),
                escapar(datos.publicacionTitulo()), escapar(monto), escapar(monto),
                escapar(datos.referencia()), escapar(aprobado), escapar(emitido));
    }

    private byte[] pdf(String html) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (conArchivo) {
                builder.useFont(() -> recurso(REGULAR), "Archivo");
                builder.useFont(() -> recurso(BOLD), "ArchivoNegrita");
            }
            builder.withHtmlContent(html, null);
            builder.toStream(salida);
            builder.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private static InputStream recurso(String ruta) {
        return GeneradorDocumentosHtml.class.getResourceAsStream(ruta);
    }

    static String dinero(BigDecimal monto) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES_CO);
        formato.setMinimumFractionDigits(0);
        formato.setMaximumFractionDigits(0);
        return "$ " + formato.format(monto == null ? BigDecimal.ZERO : monto);
    }

    static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Expuesta para pruebas: si es true, las fuentes Archivo viajaron embebidas en el PDF. */
    public boolean usaArchivo() {
        return conArchivo;
    }
}
