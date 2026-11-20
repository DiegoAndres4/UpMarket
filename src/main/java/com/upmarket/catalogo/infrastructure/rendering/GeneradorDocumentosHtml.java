package com.upmarket.catalogo.infrastructure.rendering;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.upmarket.catalogo.application.port.in.admin.EstadisticasAdmin;
import com.upmarket.catalogo.application.port.out.DatosRecibo;
import com.upmarket.catalogo.application.port.out.DatosReporte;
import com.upmarket.catalogo.application.port.out.GeneradorDocumentosPort;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
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
    private static final String ESPACIO_NOMBRES = "https://upmarket.local/schema/estadisticas/1";

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

    @Override
    public byte[] reporteEstadisticasPdf(DatosReporte datos) {
        return pdf(reporte(datos));
    }

    @Override
    public byte[] reporteEstadisticasXml(DatosReporte datos) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(salida, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            w.setDefaultNamespace(ESPACIO_NOMBRES);
            w.writeStartElement("", "reporteEstadisticas", ESPACIO_NOMBRES);
            w.writeDefaultNamespace(ESPACIO_NOMBRES);
            w.writeAttribute("version", "1.0");
            w.writeAttribute("numero", datos.numero());
            w.writeAttribute("generadoEn", OffsetDateTime.ofInstant(datos.generadoEn(), BOGOTA)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            w.writeAttribute("zonaHoraria", "America/Bogota");
            texto(w, "generadoPor", datos.generadoPor());

            EstadisticasAdmin e = datos.estadisticas();
            w.writeStartElement("", "publicaciones", ESPACIO_NOMBRES);
            w.writeAttribute("total", Long.toString(e.totalPublicaciones()));
            porEstado(w, "porEstado", e.publicacionesPorEstado());
            porEstado(w, "porEstadoPago", e.publicacionesPorEstadoPago());
            w.writeStartElement("", "porCategoria", ESPACIO_NOMBRES);
            for (Map.Entry<String, Long> categoria : new TreeMap<>(e.publicacionesPorCategoria()).entrySet()) {
                w.writeStartElement("", "categoria", ESPACIO_NOMBRES);
                w.writeAttribute("nombre", categoria.getKey());
                w.writeAttribute("cantidad", Long.toString(categoria.getValue()));
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeStartElement("", "porDia", ESPACIO_NOMBRES);
            for (EstadisticasAdmin.ConteoDia dia : e.publicacionesPorDia()) {
                w.writeStartElement("", "dia", ESPACIO_NOMBRES);
                w.writeAttribute("fecha", dia.fecha());
                w.writeAttribute("cantidad", Long.toString(dia.total()));
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeEndElement(); // publicaciones

            long pendientes = e.publicacionesPorEstadoPago().getOrDefault("PENDIENTE", 0L);
            long rechazados = e.publicacionesPorEstadoPago().getOrDefault("RECHAZADO", 0L);
            w.writeStartElement("", "pagos", ESPACIO_NOMBRES);
            w.writeAttribute("aprobados", Long.toString(e.pagosAprobados()));
            w.writeAttribute("pendientes", Long.toString(pendientes));
            w.writeAttribute("rechazados", Long.toString(rechazados));
            w.writeStartElement("", "ingresosSimulados", ESPACIO_NOMBRES);
            w.writeAttribute("moneda", "COP");
            BigDecimal ingresos = e.ingresosAprobados() == null ? BigDecimal.ZERO : e.ingresosAprobados();
            w.writeCharacters(ingresos.setScale(2).toPlainString());
            w.writeEndElement();
            w.writeEndElement();

            w.writeStartElement("", "vistas", ESPACIO_NOMBRES);
            w.writeAttribute("total", Long.toString(e.vistasTotales()));
            w.writeStartElement("", "masVistas", ESPACIO_NOMBRES);
            for (EstadisticasAdmin.VistaPublicacion publicacion : e.topPublicaciones()) {
                w.writeStartElement("", "publicacion", ESPACIO_NOMBRES);
                w.writeAttribute("id", Long.toString(publicacion.id()));
                w.writeAttribute("titulo", publicacion.titulo());
                w.writeAttribute("categoria", publicacion.categoria() == null ? "" : publicacion.categoria());
                w.writeAttribute("vistas", Long.toString(publicacion.vistas()));
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeEndElement();

            w.writeStartElement("", "perfiles", ESPACIO_NOMBRES);
            w.writeAttribute("total", Long.toString(e.totalPerfiles()));
            w.writeEndElement();
            w.writeStartElement("", "resenas", ESPACIO_NOMBRES);
            w.writeAttribute("total", Long.toString(e.totalResenas()));
            w.writeEndElement();

            w.writeStartElement("", "notas", ESPACIO_NOMBRES);
            for (String nota : datos.notas()) {
                texto(w, "nota", nota);
            }
            w.writeEndElement();

            w.writeEndElement(); // reporteEstadisticas
            w.writeEndDocument();
            w.flush();
            w.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el XML del reporte", e);
        }
    }

    private static void porEstado(XMLStreamWriter escritor, String nombre, Map<String, Long> valores)
            throws XMLStreamException {
        escritor.writeStartElement("", nombre, ESPACIO_NOMBRES);
        for (Map.Entry<String, Long> estado : valores.entrySet()) {
            escritor.writeStartElement("", "estado", ESPACIO_NOMBRES);
            escritor.writeAttribute("nombre", estado.getKey());
            escritor.writeAttribute("cantidad", Long.toString(estado.getValue()));
            escritor.writeEndElement();
        }
        escritor.writeEndElement();
    }

    private static void texto(XMLStreamWriter escritor, String nombre, String valor) throws XMLStreamException {
        escritor.writeStartElement("", nombre, ESPACIO_NOMBRES);
        escritor.writeCharacters(valor == null ? "" : valor);
        escritor.writeEndElement();
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
                .nota { margin-top: 8mm; font-size: 9pt; color: #1A1A1A;
                        background: #FAF7F0; padding: 4mm 5mm; }
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

    private String reporte(DatosReporte datos) {
        EstadisticasAdmin e = datos.estadisticas();
        long maxCategoria = Math.max(1,
                e.publicacionesPorCategoria().values().stream().mapToLong(Long::longValue).max().orElse(0));

        String categorias = e.publicacionesPorCategoria().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entrada -> barra(entrada.getKey(), entrada.getValue(), maxCategoria))
                .collect(Collectors.joining());
        String graficoDias = graficoDias(e);

        StringBuilder filasTop = new StringBuilder();
        int puesto = 1;
        for (EstadisticasAdmin.VistaPublicacion publicacion : e.topPublicaciones()) {
            filasTop.append("<tr><td class=\"derecha\">").append(puesto++)
                    .append("</td><td>").append(escapar(publicacion.titulo()))
                    .append("</td><td>").append(escapar(publicacion.categoria()))
                    .append("</td><td class=\"derecha\">").append(numero(publicacion.vistas()))
                    .append("</td></tr>");
        }

        long pendientes = e.publicacionesPorEstadoPago().getOrDefault("PENDIENTE", 0L);
        long rechazados = e.publicacionesPorEstadoPago().getOrDefault("RECHAZADO", 0L);
        String emitido = FECHA_HORA.format(datos.generadoEn());

        StringBuilder h = new StringBuilder(16384);
        h.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\" /><style>")
                .append(estilos())
                .append("</style></head><body>");
        h.append("<table class=\"cabecera\"><tr><td>")
                .append("<div class=\"marca negrita\"><span class=\"tinta\">UP</span>")
                .append("<span class=\"rojo\">Market</span></div>")
                .append("<div style=\"margin-top:2mm; color:#4A4A4A; font-size:9pt;\">")
                .append("Universidad de Pamplona · Villa del Rosario</div></td>")
                .append("<td class=\"meta\"><div class=\"titulo negrita\">Reporte de estadísticas</div>")
                .append("<div style=\"margin-top:1.5mm;\"><span class=\"negrita\">N.º</span> ")
                .append(escapar(datos.numero())).append("</div>")
                .append("<div><span class=\"negrita\">Generado el</span> ").append(escapar(emitido)).append("</div>")
                .append("<div><span class=\"negrita\">Generado por</span> ")
                .append(escapar(datos.generadoPor())).append("</div></td></tr></table>");

        h.append("<table class=\"kpis\"><tr>")
                .append(cifra("Publicaciones", numero(e.totalPublicaciones()),
                        numero(e.publicacionesPorEstado().getOrDefault("DISPONIBLE", 0L)) + " disponibles · "
                                + numero(e.publicacionesPorEstado().getOrDefault("VENDIDA", 0L)) + " vendidas", null))
                .append(cifra("Vistas acumuladas", numero(e.vistasTotales()), null, null))
                .append(cifra("Perfiles de usuario", numero(e.totalPerfiles()), null, null))
                .append("</tr><tr>")
                .append(cifra("Reseñas", numero(e.totalResenas()), null, null))
                .append(cifra("Pagos aprobados", numero(e.pagosAprobados()),
                        numero(pendientes) + " pendientes · " + numero(rechazados) + " rechazados", null))
                .append(cifra("Ingresos simulados", dinero(e.ingresosAprobados()), null,
                        "Suma de pagos aprobados en la app. El pago aún es simulado."))
                .append("</tr><tr>")
                .append(cifra("Categorías con publicaciones",
                        numero(e.publicacionesPorCategoria().size()), null, null))
                .append("<td></td><td></td></tr></table>");

        h.append("<div class=\"seccion negrita\">Publicaciones por día (últimos 30 días)</div>").append(graficoDias);
        h.append("<div class=\"seccion negrita\">Publicaciones por categoría</div>").append(categorias);
        h.append("<div class=\"seccion negrita\">Publicaciones más vistas</div>")
                .append("<table class=\"datos\"><thead><tr><th class=\"derecha\">#</th><th>Título</th>")
                .append("<th>Categoría</th><th class=\"derecha\">Vistas</th></tr></thead><tbody>")
                .append(filasTop).append("</tbody></table>");

        h.append("<div class=\"notas\"><div class=\"negrita\">Notas</div>");
        for (String nota : datos.notas()) {
            h.append("<div>· ").append(escapar(nota)).append("</div>");
        }
        h.append("</div></body></html>");
        return h.toString();
    }

    private String estilos() {
        return """
                @page { size: A4 portrait; margin: 18mm; }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: '%s', Helvetica, Arial, sans-serif; color: #1A1A1A;
                       font-size: 9.5pt; line-height: 1.4; }
                .negrita { font-family: '%s', Helvetica, Arial, sans-serif; font-weight: 700; }
                .cabecera { width: 100%%; border-collapse: collapse; }
                .cabecera td { vertical-align: top; }
                .marca { font-size: 24pt; letter-spacing: -0.045em; line-height: 1; }
                .marca .rojo { color: #D62828; }
                .marca .tinta { color: #1A1A1A; }
                .meta { text-align: right; color: #4A4A4A; font-size: 9pt; }
                .meta .titulo { color: #1A1A1A; font-size: 14pt; }
                .kpis { width: 100%%; border-collapse: collapse; margin-top: 6mm; }
                .kpis td { padding: 0 2mm 2mm 0; vertical-align: top; }
                .kpis td.cifra { width: 33.33%%; background: #FAF7F0; border: 0.5pt solid #E5E0D5;
                                 padding: 3.5mm; }
                .kpi-titulo { font-size: 8.5pt; color: #4A4A4A; }
                .kpi-valor { font-size: 16pt; letter-spacing: -0.02em; margin-top: 1mm; }
                .kpi-detalle { font-size: 8.5pt; color: #4A4A4A; margin-top: 1mm; }
                .kpi-nota { font-size: 7.5pt; color: #4A4A4A; margin-top: 1.5mm; }
                .seccion { font-size: 12pt; margin: 7mm 0 3mm; }
                .barra { width: 100%%; border-collapse: collapse; margin-bottom: 1.4mm; }
                .barra td { padding: 0; vertical-align: middle; }
                .barra .etq { width: 46mm; font-size: 8pt; padding-right: 3mm; }
                .barra .pista { background: #F2EDE3; height: 4mm; }
                .barra .relleno { background: #F5B700; height: 4mm; }
                .barra .val { width: 12mm; text-align: right; font-size: 8pt; padding-left: 2mm; }
                .grafico-dias { width: 100%%; border-collapse: collapse; margin-top: 2mm; }
                .grafico-dias .eje { width: 13mm; vertical-align: bottom; padding-right: 2mm;
                                     text-align: right; font-size: 7.5pt; color: #4A4A4A; }
                .grafico-dias .eje .maximo { font-size: 11pt; color: #1A1A1A; }
                .grafico-dias .area { vertical-align: bottom; }
                .barras-dia { width: 100%%; border-collapse: collapse; table-layout: fixed; }
                .barras-dia td { padding: 0; text-align: center; vertical-align: bottom; }
                .barras-dia tr.pistas td { height: 34mm; background: #F2EDE3; }
                .barras-dia td.pista { vertical-align: bottom; }
                .barras-dia .columna { text-align: center; }
                .barras-dia .valor-barra { height: 3.4mm; font-size: 5.5pt; line-height: 3.4mm; color: #1A1A1A; }
                .barras-dia .relleno { width: 68%%; margin: 0 auto; background: #F5B700; }
                .barras-dia tr.etiquetas td { height: 4mm; font-size: 5.5pt; color: #4A4A4A; white-space: nowrap; }
                .total-periodo { margin-top: 2.5mm; font-size: 8.5pt; color: #4A4A4A; }
                table.datos { width: 100%%; border-collapse: collapse; margin-top: 3mm; }
                table.datos th { background: #F2EDE3; text-align: left; padding: 2.6mm 2.5mm; font-size: 8.5pt; }
                table.datos td { padding: 2.6mm 2.5mm; border-bottom: 0.4pt solid #E5E0D5; font-size: 8.5pt; }
                .derecha { text-align: right; }
                .notas { margin-top: 7mm; background: #FAF7F0; padding: 4mm 5mm; font-size: 8.5pt; color: #4A4A4A; }
                .notas div { margin-bottom: 1mm; }
                """.formatted(familia, familiaNegrita);
    }

    private static String cifra(String titulo, String valor, String detalle, String nota) {
        StringBuilder celda = new StringBuilder("<td class=\"cifra\"><div class=\"kpi-titulo\">")
                .append(escapar(titulo)).append("</div><div class=\"kpi-valor negrita\">")
                .append(escapar(valor)).append("</div>");
        if (detalle != null) {
            celda.append("<div class=\"kpi-detalle\">").append(escapar(detalle)).append("</div>");
        }
        if (nota != null) {
            celda.append("<div class=\"kpi-nota\">").append(escapar(nota)).append("</div>");
        }
        return celda.append("</td>").toString();
    }

    private static String barra(String etiqueta, long valor, long maximo) {
        int porcentaje = maximo <= 0 ? 0 : (int) Math.round(valor * 100.0 / maximo);
        return String.format(Locale.ROOT,
                "<table class=\"barra\"><tr><td class=\"etq\">%s</td>"
                        + "<td class=\"pista\"><div class=\"relleno\" style=\"width:%d%%\"></div></td>"
                        + "<td class=\"val\">%s</td></tr></table>",
                escapar(etiqueta), porcentaje, numero(valor));
    }

    /**
     * Un solo bloque compacto para los últimos 30 días: 30 barras verticales en una tabla de
     * altura fija, con la columna alineada abajo. Sin flexbox (openhtmltopdf no lo soporta).
     */
    private static String graficoDias(EstadisticasAdmin e) {
        List<EstadisticasAdmin.ConteoDia> dias = e.publicacionesPorDia();
        long maxReal = dias.stream().mapToLong(EstadisticasAdmin.ConteoDia::total).max().orElse(0);
        long maximo = Math.max(1, maxReal);
        long total = dias.stream().mapToLong(EstadisticasAdmin.ConteoDia::total).sum();

        StringBuilder pistas = new StringBuilder();
        StringBuilder etiquetas = new StringBuilder();
        for (int i = 0; i < dias.size(); i++) {
            EstadisticasAdmin.ConteoDia dia = dias.get(i);
            int alto = dia.total() <= 0 ? 0 : (int) Math.max(1, Math.round(dia.total() * 30.0 / maximo));
            pistas.append("<td class=\"pista\"><div class=\"columna\"><div class=\"valor-barra\">")
                    .append(dia.total() > 0 ? numero(dia.total()) : "&#160;")
                    .append("</div><div class=\"relleno\" style=\"height:").append(alto)
                    .append("mm;\"></div></div></td>");
            String fecha = dia.fecha();
            String etiqueta = i % 7 == 0 && fecha != null && fecha.length() >= 10
                    ? fecha.substring(8, 10) + "/" + fecha.substring(5, 7)
                    : "&#160;";
            etiquetas.append("<td class=\"etiqueta\">").append(etiqueta).append("</td>");
        }

        return "<table class=\"grafico-dias\"><tr>"
                + "<td class=\"eje\"><div>Máx.</div><div class=\"maximo negrita\">" + numero(maxReal)
                + "</div></td>"
                + "<td class=\"area\"><table class=\"barras-dia\">"
                + "<tr class=\"pistas\">" + pistas + "</tr>"
                + "<tr class=\"etiquetas\">" + etiquetas + "</tr>"
                + "</table></td></tr></table>"
                + "<div class=\"total-periodo\">Total en el periodo: " + numero(total)
                + " publicaciones</div>";
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

    static String numero(long valor) {
        return NumberFormat.getIntegerInstance(ES_CO).format(valor);
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
