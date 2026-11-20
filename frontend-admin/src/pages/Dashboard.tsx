import { useEffect, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Download, Eye } from 'lucide-react'
import { api, descargarDocumento } from '../lib/api'
import { fecha, numero, precio } from '../lib/formato'
import type { DocumentoAdmin, Estadisticas, Pagina, ReporteAdmin, ReporteGenerado } from '../lib/tipos'
import { DocumentoViewer, type DocumentoVista } from '../components/DocumentoViewer'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

/* Paleta de la marca: ámbar para la serie principal, rojo para el énfasis, tinta y arena para ejes. */
const AMBAR = '#F5B700'
const TINTA = '#1A1A1A'
const GRIS_EJE = '#4A4A4A'
const ARENA = '#E5E0D5'

function Cifra({ titulo, valor, detalle, nota }: { titulo: string; valor: string; detalle?: string; nota?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{titulo}</CardDescription>
        <CardTitle className="text-3xl font-extrabold tabular-nums tracking-tight text-foreground">{valor}</CardTitle>
      </CardHeader>
      {detalle || nota ? (
        <CardContent className="space-y-1">
          {detalle ? <p className="text-xs text-muted-foreground tabular-nums">{detalle}</p> : null}
          {nota ? <p className="text-xs leading-relaxed text-muted-foreground">{nota}</p> : null}
        </CardContent>
      ) : null}
    </Card>
  )
}

export default function Dashboard() {
  const [datos, setDatos] = useState<Estadisticas | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)
  const [reportes, setReportes] = useState<Pagina<ReporteAdmin> | null>(null)
  const [cargandoReportes, setCargandoReportes] = useState(true)
  const [errorReportes, setErrorReportes] = useState<string | null>(null)
  const [generandoReporte, setGenerandoReporte] = useState(false)
  const [ultimoReporte, setUltimoReporte] = useState<ReporteGenerado | null>(null)
  const [avisoReporte, setAvisoReporte] = useState<string | null>(null)
  const [errorAccionReporte, setErrorAccionReporte] = useState<string | null>(null)
  const [recargaReportes, setRecargaReportes] = useState(0)
  const [vista, setVista] = useState<DocumentoVista | null>(null)

  const cargar = () => {
    setCargando(true)
    setError(null)
    api<Estadisticas>('/admin/estadisticas')
      .then(setDatos)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'No se pudieron cargar las estadísticas'))
      .finally(() => setCargando(false))
  }

  useEffect(() => {
    cargar()
  }, [])

  useEffect(() => {
    let vigente = true
    setCargandoReportes(true)
    setErrorReportes(null)
    api<Pagina<ReporteAdmin>>('/admin/reportes?pagina=1&tamano=5')
      .then((resultado) => {
        if (vigente) setReportes(resultado)
      })
      .catch((e: unknown) => {
        if (vigente) setErrorReportes(e instanceof Error ? e.message : 'No se pudieron cargar los reportes')
      })
      .finally(() => {
        if (vigente) setCargandoReportes(false)
      })
    return () => {
      vigente = false
    }
  }, [recargaReportes])

  const generarReporte = async () => {
    setGenerandoReporte(true)
    setAvisoReporte(null)
    setErrorAccionReporte(null)
    try {
      const reporte = await api<ReporteGenerado>('/admin/reportes/estadisticas', { method: 'POST' })
      setUltimoReporte(reporte)
      setAvisoReporte(`Reporte ${reporte.numero} generado. Ya puedes descargar el PDF y el XML.`)
      setRecargaReportes((valor) => valor + 1)
    } catch (e) {
      setErrorAccionReporte(e instanceof Error ? e.message : 'No se pudo generar el reporte')
    } finally {
      setGenerandoReporte(false)
    }
  }

  const ver = (documento: DocumentoAdmin, numeroReporte: string) => {
    setVista({
      id: documento.id,
      numero: numeroReporte,
      formato: documento.formato,
      nombreArchivo: documento.nombreArchivo,
      tipo: 'Reporte de estadísticas',
    })
  }

  if (cargando) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-9 w-64" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
        <Skeleton className="h-80" />
      </div>
    )
  }

  if (error || !datos) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No se pudo cargar el tablero</CardTitle>
          <CardDescription>{error ?? 'Respuesta vacía del servidor'}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={cargar}>Reintentar</Button>
        </CardContent>
      </Card>
    )
  }

  const categorias = Object.entries(datos.publicacionesPorCategoria).map(([nombre, total]) => ({ nombre, total }))
  const pagos = datos.publicacionesPorEstadoPago

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-extrabold tracking-tight">Tablero</h2>
        <p className="text-sm text-muted-foreground">Estado general de la comunidad en tiempo real.</p>
      </header>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Cifras principales">
        <Cifra titulo="Publicaciones" valor={numero(datos.totalPublicaciones)}
          detalle={`${numero(datos.publicacionesPorEstado.DISPONIBLE)} disponibles · ${numero(datos.publicacionesPorEstado.VENDIDA)} vendidas`} />
        <Cifra titulo="Vistas acumuladas" valor={numero(datos.vistasTotales)} />
        <Cifra titulo="Perfiles de usuario" valor={numero(datos.totalPerfiles)} detalle={`${numero(datos.totalResenas)} reseñas`} />
        <Cifra titulo="Pagos aprobados" valor={numero(datos.pagosAprobados)}
          detalle={`${numero(pagos.PENDIENTE)} pendientes · ${numero(pagos.RECHAZADO)} rechazados`}
          nota="Las publicaciones de demostración se activaron sin registro de pago." />
        <Cifra titulo="Ingresos simulados" valor={precio(datos.ingresosAprobados)}
          nota="Suma de pagos aprobados en la app. El pago aún es simulado." />
        <Cifra titulo="Publicaciones por categoría" valor={String(categorias.length)} detalle="Categorías con publicaciones" />
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Publicaciones por día</CardTitle>
            <CardDescription>Últimos 30 días, en la zona horaria del negocio.</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={datos.publicacionesPorDia}>
                <CartesianGrid strokeDasharray="3 3" stroke={ARENA} vertical={false} />
                <XAxis
                  dataKey="fecha"
                  tickFormatter={(fecha: string) => fecha.slice(5)}
                  fontSize={12}
                  stroke={ARENA}
                  tick={{ fill: GRIS_EJE }}
                />
                <YAxis
                  allowDecimals={false}
                  fontSize={12}
                  stroke={ARENA}
                  tick={{ fill: GRIS_EJE }}
                />
                <Tooltip
                  contentStyle={{ background: '#FFFFFF', border: `1px solid ${ARENA}`, borderRadius: 8 }}
                  labelStyle={{ color: TINTA }}
                  itemStyle={{ color: TINTA }}
                  labelFormatter={(fecha) => `Fecha: ${fecha}`}
                />
                <Bar dataKey="total" name="Publicaciones" fill={AMBAR} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Publicaciones por categoría</CardTitle>
            <CardDescription>Todas las publicaciones, incluidas las pendientes de pago.</CardDescription>
          </CardHeader>
          <CardContent>
            {categorias.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">Todavía no hay publicaciones.</p>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={categorias} layout="vertical" margin={{ left: 8, right: 16 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={ARENA} horizontal={false} />
                  <XAxis
                    type="number"
                    allowDecimals={false}
                    fontSize={12}
                    stroke={ARENA}
                    tick={{ fill: GRIS_EJE }}
                  />
                  <YAxis
                    type="category"
                    dataKey="nombre"
                    width={110}
                    fontSize={12}
                    stroke={ARENA}
                    tick={{ fill: GRIS_EJE }}
                  />
                  <Tooltip
                    contentStyle={{ background: '#FFFFFF', border: `1px solid ${ARENA}`, borderRadius: 8 }}
                    labelStyle={{ color: TINTA }}
                    itemStyle={{ color: TINTA }}
                  />
                  <Bar dataKey="total" name="Publicaciones" fill={AMBAR} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Publicaciones más vistas</CardTitle>
          <CardDescription>El top 5 ayuda a entender qué busca la gente.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Publicación</TableHead>
                <TableHead className="text-right">Vistas</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {datos.topPublicaciones.map((publicacion, indice) => (
                <TableRow key={publicacion.id}>
                  <TableCell>
                    <span className="mr-2 text-muted-foreground tabular-nums">#{indice + 1}</span>
                    {publicacion.titulo}
                  </TableCell>
                  <TableCell className="text-right">
                    <Badge variant={indice === 0 ? 'exito' : 'secondary'}>{numero(publicacion.vistas)}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Reportes de estadísticas</CardTitle>
          <CardDescription>
            Genera un PDF y un XML con las mismas cifras del tablero, guardados de forma privada.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={() => void generarReporte()} disabled={generandoReporte}>
              {generandoReporte ? 'Generando…' : 'Generar reporte (PDF y XML)'}
            </Button>
            {ultimoReporte ? (
              <>
                <Button variant="outline" onClick={() => ver(ultimoReporte.pdf, ultimoReporte.numero)}>
                  <Eye className="h-4 w-4" /> Ver PDF
                </Button>
                <Button variant="outline" onClick={() => void descargarDocumento(ultimoReporte.pdf)}>
                  <Download className="h-4 w-4" /> Descargar PDF
                </Button>
                <Button variant="outline" onClick={() => ver(ultimoReporte.xml, ultimoReporte.numero)}>
                  <Eye className="h-4 w-4" /> Ver XML
                </Button>
                <Button variant="outline" onClick={() => void descargarDocumento(ultimoReporte.xml)}>
                  <Download className="h-4 w-4" /> Descargar XML
                </Button>
              </>
            ) : null}
          </div>
          {avisoReporte ? <p className="text-sm" role="status">{avisoReporte}</p> : null}
          {errorAccionReporte ? <p className="text-sm text-destructive" role="alert">{errorAccionReporte}</p> : null}

          <div>
            <h3 className="mb-2 text-sm font-semibold">Reportes generados</h3>
            {cargandoReportes ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
              </div>
            ) : errorReportes ? (
              <div className="space-y-2">
                <p className="text-sm text-destructive">{errorReportes}</p>
                <Button variant="outline" size="sm" onClick={() => setRecargaReportes((valor) => valor + 1)}>
                  Reintentar
                </Button>
              </div>
            ) : !reportes || reportes.contenido.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">Todavía no hay reportes generados.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Fecha</TableHead>
                    <TableHead>Número</TableHead>
                    <TableHead>Generado por</TableHead>
                    <TableHead className="text-right">Ver / descargar</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {reportes.contenido.map((reporte) => (
                    <TableRow key={reporte.numero}>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{fecha(reporte.generadoEn)}</TableCell>
                      <TableCell className="font-medium">{reporte.numero}</TableCell>
                      <TableCell className="max-w-[180px] truncate" title={reporte.generadoPor}>{reporte.generadoPor}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-col items-end gap-2">
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" disabled={!reporte.pdf}
                              onClick={() => reporte.pdf ? ver(reporte.pdf, reporte.numero) : undefined}>
                              <Eye className="h-4 w-4" /> Ver PDF
                            </Button>
                            <Button variant="outline" size="sm" disabled={!reporte.pdf}
                              onClick={() => reporte.pdf ? void descargarDocumento(reporte.pdf) : undefined}>
                              Descargar PDF
                            </Button>
                          </div>
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" disabled={!reporte.xml}
                              onClick={() => reporte.xml ? ver(reporte.xml, reporte.numero) : undefined}>
                              <Eye className="h-4 w-4" /> Ver XML
                            </Button>
                            <Button variant="outline" size="sm" disabled={!reporte.xml}
                              onClick={() => reporte.xml ? void descargarDocumento(reporte.xml) : undefined}>
                              Descargar XML
                            </Button>
                          </div>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>
        </CardContent>
      </Card>

      <DocumentoViewer documento={vista} onCerrar={() => setVista(null)} />
    </div>
  )
}
