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
import { Download, Eye, Trash2 } from 'lucide-react'
import { api, descargarDocumento } from '../lib/api'
import { fecha, numero, precio } from '../lib/formato'
import { useTema } from '../lib/tema'
import type { DocumentoAdmin, Estadisticas, Pagina, ReporteAdmin, ReporteGenerado } from '../lib/tipos'
import { DocumentoViewer, type DocumentoVista } from '../components/DocumentoViewer'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '../components/ui/alert-dialog'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton, SkeletonFilas } from '../components/ui/skeleton'
import { Spinner } from '../components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

/*
 * Paleta de la marca por tema: el ámbar de las barras se mantiene; tinta, ejes y tooltip
 * cambian para conservar el contraste sobre las superficies oscuras.
 */
const PALETAS = {
  claro: {
    barra: '#F5B700',
    tinta: '#1A1A1A',
    eje: '#4A4A4A',
    rejilla: '#E5E0D5',
    tooltipFondo: '#FFFFFF',
    tooltipBorde: '#E5E0D5',
  },
  oscuro: {
    barra: '#F5B700',
    tinta: '#F3EFE6',
    eje: '#C2BBAE',
    rejilla: '#3A372F',
    tooltipFondo: '#26241F',
    tooltipBorde: '#3A372F',
  },
} as const

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
  const tema = useTema()
  const paleta = PALETAS[tema]
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
  const [reporteAEliminar, setReporteAEliminar] = useState<ReporteAdmin | null>(null)
  const [eliminando, setEliminando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<string | null>(null)
  const [descargando, setDescargando] = useState<number | null>(null)

  const descargar = async (documento: DocumentoAdmin) => {
    setDescargando(documento.id)
    setAvisoReporte(null)
    setErrorAccionReporte(null)
    try {
      await descargarDocumento(documento)
    } catch (e) {
      setErrorAccionReporte(e instanceof Error ? e.message : 'No se pudo descargar el documento')
    } finally {
      setDescargando(null)
    }
  }

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

  const eliminarReporte = async () => {
    if (!reporteAEliminar) return
    const numero = reporteAEliminar.numero
    setEliminando(true)
    setEliminandoId(numero)
    setAvisoReporte(null)
    setErrorAccionReporte(null)
    try {
      await api(`/admin/reportes/${encodeURIComponent(numero)}`, { method: 'DELETE' })
      if (ultimoReporte?.numero === numero) setUltimoReporte(null)
      setAvisoReporte('Reporte eliminado')
      setRecargaReportes((valor) => valor + 1)
    } catch (e) {
      setErrorAccionReporte(e instanceof Error ? e.message : 'No se pudo eliminar el reporte')
    } finally {
      setEliminando(false)
      setEliminandoId(null)
      setReporteAEliminar(null)
    }
  }

  const ver = (documento: DocumentoAdmin, numeroReporte: string) => {
    setVista({
      id: documento.id,
      numero: numeroReporte,
      formato: 'PDF',
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
                <CartesianGrid strokeDasharray="3 3" stroke={paleta.rejilla} vertical={false} />
                <XAxis
                  dataKey="fecha"
                  tickFormatter={(fecha: string) => fecha.slice(5)}
                  fontSize={12}
                  stroke={paleta.rejilla}
                  tick={{ fill: paleta.eje }}
                />
                <YAxis
                  allowDecimals={false}
                  fontSize={12}
                  stroke={paleta.rejilla}
                  tick={{ fill: paleta.eje }}
                />
                <Tooltip
                  contentStyle={{ background: paleta.tooltipFondo, border: `1px solid ${paleta.tooltipBorde}`, borderRadius: 8 }}
                  labelStyle={{ color: paleta.tinta }}
                  itemStyle={{ color: paleta.tinta }}
                  labelFormatter={(fecha) => `Fecha: ${fecha}`}
                />
                <Bar dataKey="total" name="Publicaciones" fill={paleta.barra} radius={[4, 4, 0, 0]} />
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
                  <CartesianGrid strokeDasharray="3 3" stroke={paleta.rejilla} horizontal={false} />
                  <XAxis
                    type="number"
                    allowDecimals={false}
                    fontSize={12}
                    stroke={paleta.rejilla}
                    tick={{ fill: paleta.eje }}
                  />
                  <YAxis
                    type="category"
                    dataKey="nombre"
                    width={110}
                    fontSize={12}
                    stroke={paleta.rejilla}
                    tick={{ fill: paleta.eje }}
                  />
                  <Tooltip
                    contentStyle={{ background: paleta.tooltipFondo, border: `1px solid ${paleta.tooltipBorde}`, borderRadius: 8 }}
                    labelStyle={{ color: paleta.tinta }}
                    itemStyle={{ color: paleta.tinta }}
                  />
                  <Bar dataKey="total" name="Publicaciones" fill={paleta.barra} radius={[0, 4, 4, 0]} />
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
              {generandoReporte ? (
                <>
                  <Spinner /> Generando…
                </>
              ) : (
                'Generar reporte (PDF y XML)'
              )}
            </Button>
            {ultimoReporte ? (
              <>
                <Button variant="outline" onClick={() => ver(ultimoReporte.pdf, ultimoReporte.numero)}>
                  <Eye className="h-4 w-4" aria-hidden="true" /> Ver PDF
                </Button>
                <Button
                  variant="outline"
                  onClick={() => void descargar(ultimoReporte.pdf)}
                  disabled={descargando === ultimoReporte.pdf.id}
                >
                  {descargando === ultimoReporte.pdf.id ? <Spinner /> : <Download className="h-4 w-4" aria-hidden="true" />}
                  {descargando === ultimoReporte.pdf.id ? 'Descargando…' : 'Descargar PDF'}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => void descargar(ultimoReporte.xml)}
                  disabled={descargando === ultimoReporte.xml.id}
                >
                  {descargando === ultimoReporte.xml.id ? <Spinner /> : <Download className="h-4 w-4" aria-hidden="true" />}
                  {descargando === ultimoReporte.xml.id ? 'Descargando…' : 'Descargar XML'}
                </Button>
              </>
            ) : null}
          </div>
          {avisoReporte ? <p className="text-sm" role="status">{avisoReporte}</p> : null}
          {errorAccionReporte ? <p className="text-sm text-destructive-text" role="alert">{errorAccionReporte}</p> : null}

          <div>
            <h3 className="mb-2 text-sm font-semibold">Reportes generados</h3>
            {cargandoReportes ? (
              <SkeletonFilas filas={3} />
            ) : errorReportes ? (
              <div className="space-y-2">
                <p className="text-sm text-destructive-text">{errorReportes}</p>
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
                              <Eye className="h-4 w-4" aria-hidden="true" /> Ver PDF
                            </Button>
                            <Button variant="outline" size="sm" disabled={!reporte.pdf || descargando === reporte.pdf.id}
                              onClick={() => reporte.pdf ? void descargar(reporte.pdf) : undefined}>
                              {reporte.pdf && descargando === reporte.pdf.id ? (
                                <>
                                  <Spinner /> Descargando…
                                </>
                              ) : (
                                'Descargar PDF'
                              )}
                            </Button>
                          </div>
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" disabled={!reporte.xml || descargando === reporte.xml.id}
                              onClick={() => reporte.xml ? void descargar(reporte.xml) : undefined}>
                              {reporte.xml && descargando === reporte.xml.id ? (
                                <>
                                  <Spinner /> Descargando…
                                </>
                              ) : (
                                'Descargar XML'
                              )}
                            </Button>
                            <Button variant="destructive" size="sm" disabled={eliminandoId === reporte.numero}
                              onClick={() => setReporteAEliminar(reporte)}>
                              {eliminandoId === reporte.numero ? (
                                <>
                                  <Spinner /> Eliminando…
                                </>
                              ) : (
                                <>
                                  <Trash2 className="h-4 w-4" aria-hidden="true" /> Eliminar
                                </>
                              )}
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

      <AlertDialog open={reporteAEliminar !== null}
        onOpenChange={(abierto) => { if (!abierto && !eliminando) setReporteAEliminar(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>¿Eliminar el reporte {reporteAEliminar?.numero}?</AlertDialogTitle>
            <AlertDialogDescription>
              Se borrarán el PDF y el XML de Cloudinary. Esta acción no se puede deshacer.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={eliminando}>Cancelar</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={eliminando} onClick={() => void eliminarReporte()}>
              {eliminando ? (
                <>
                  <Spinner /> Eliminando…
                </>
              ) : (
                'Eliminar reporte'
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
