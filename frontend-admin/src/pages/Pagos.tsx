import { useEffect, useState } from 'react'
import { Download, Eye, FileText } from 'lucide-react'
import { api, descargarDocumento } from '../lib/api'
import { fecha, fechaCorta, numero, precio } from '../lib/formato'
import type { DocumentoAdmin, EstadoPago, Pagina, PagoAdmin } from '../lib/tipos'
import { DocumentoViewer, type DocumentoVista } from '../components/DocumentoViewer'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { SkeletonFilas } from '../components/ui/skeleton'
import { Spinner } from '../components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10
const TOTAL_COLUMNAS = 7

function Estado({ estado }: { estado: EstadoPago }) {
  if (estado === 'APROBADO') return <Badge variant="exito">Aprobado</Badge>
  if (estado === 'PENDIENTE') return <Badge variant="alerta">Pendiente</Badge>
  return <Badge variant="destructive">Rechazado</Badge>
}

function CuentaEliminada() {
  return (
    <Badge variant="secondary" className="bg-muted text-muted-foreground">
      Cuenta eliminada
    </Badge>
  )
}

function AccionesRecibo({
  pago,
  generando,
  descargando,
  onVer,
  onDescargar,
  onGenerar,
}: {
  pago: PagoAdmin
  generando: number | null
  descargando: number | null
  onVer: (pago: PagoAdmin) => void
  onDescargar: (pago: PagoAdmin) => Promise<void>
  onGenerar: (pago: PagoAdmin) => Promise<void>
}) {
  if (pago.estado !== 'APROBADO') return <span className="text-muted-foreground">—</span>
  const recibo = pago.recibo
  if (!recibo.existe || !recibo.documentoId) {
    const pendiente = generando === pago.id
    return (
      <Button size="sm" disabled={pendiente} onClick={() => void onGenerar(pago)}>
        {pendiente ? <Spinner /> : <FileText className="h-4 w-4" aria-hidden="true" />}
        {pendiente ? 'Generando…' : 'Generar recibo'}
      </Button>
    )
  }
  const bajando = descargando === pago.id
  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => onVer(pago)} aria-label={`Ver recibo ${recibo.numero ?? ''}`}>
          <Eye className="h-4 w-4" aria-hidden="true" /> Ver
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={bajando}
          onClick={() => void onDescargar(pago)}
          aria-label={`Descargar recibo ${recibo.numero ?? ''}`}
        >
          {bajando ? <Spinner /> : <Download className="h-4 w-4" aria-hidden="true" />}
          {bajando ? 'Descargando…' : 'Descargar'}
        </Button>
      </div>
      {recibo.numero ? <span className="text-xs text-muted-foreground">{recibo.numero}</span> : null}
    </div>
  )
}

export default function Pagos() {
  const [pagina, setPagina] = useState(1)
  const [datos, setDatos] = useState<Pagina<PagoAdmin> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [generando, setGenerando] = useState<number | null>(null)
  const [descargando, setDescargando] = useState<number | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [vista, setVista] = useState<DocumentoVista | null>(null)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    api<Pagina<PagoAdmin>>(`/admin/pagos?pagina=${pagina}&tamano=${TAMANO}`)
      .then((resultado) => {
        if (vigente) setDatos(resultado)
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : 'No se pudieron cargar los pagos')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [pagina, recarga])

  const generar = async (pago: PagoAdmin) => {
    setGenerando(pago.id)
    setAviso(null)
    setErrorAccion(null)
    try {
      const documento = await api<DocumentoAdmin>(`/admin/pagos/${pago.id}/recibo`, { method: 'POST' })
      await descargarDocumento(documento)
      setAviso(`Recibo ${documento.numero} generado y descargado.`)
      setRecarga((valor) => valor + 1)
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : 'No se pudo generar el recibo')
    } finally {
      setGenerando(null)
    }
  }

  const descargar = async (pago: PagoAdmin) => {
    const recibo = pago.recibo
    if (!recibo.documentoId) return
    setAviso(null)
    setErrorAccion(null)
    setDescargando(pago.id)
    try {
      await descargarDocumento({
        id: recibo.documentoId,
        nombreArchivo: recibo.nombreArchivo ?? 'recibo.pdf',
      })
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : 'No se pudo descargar el recibo')
    } finally {
      setDescargando(null)
    }
  }

  const ver = (pago: PagoAdmin) => {
    const recibo = pago.recibo
    if (!recibo.documentoId || !recibo.numero) return
    setVista({
      id: recibo.documentoId,
      numero: recibo.numero,
      formato: 'PDF',
      nombreArchivo: recibo.nombreArchivo ?? `recibo-${recibo.numero}.pdf`,
      tipo: 'Recibo de pago',
    })
  }

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.total / datos.tamano)) : 1
  const vacio = datos?.contenido.length === 0

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-extrabold tracking-tight">Pagos</h2>
        <p className="text-sm text-muted-foreground">
          Pagos simulados de la tarifa de publicación. Solo los pagos aprobados tienen recibo.
        </p>
      </header>

      {aviso ? (
        <p className="rounded-md border border-border bg-secondary px-3 py-2 text-sm" role="status">
          {aviso}
        </p>
      ) : null}
      {errorAccion ? (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive-text" role="alert">
          {errorAccion}
        </p>
      ) : null}

      {error ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive-text">{error}</p>
            <Button className="mt-3" variant="outline" onClick={() => setRecarga((valor) => valor + 1)}>
              Reintentar
            </Button>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle>Listado de pagos</CardTitle>
            <CardDescription>
              {datos ? `${numero(datos.total)} pagos · página ${datos.pagina} de ${totalPaginas}` : 'Cargando…'}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {cargando ? (
            <SkeletonFilas filas={6} />
          ) : datos ? (
            <>
              <ul className="divide-y xl:hidden">
                {vacio ? (
                  <li className="px-4 py-10 text-center text-muted-foreground">
                    Aún no hay pagos. Los pagos aparecen cuando un estudiante paga su publicación.
                  </li>
                ) : null}
                {datos.contenido.map((pago) => (
                  <li key={pago.id} className="space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate font-medium">
                          {pago.publicacion?.titulo ?? 'Publicación eliminada'}
                          {pago.publicacion ? <span className="ml-2 text-xs text-muted-foreground">#{pago.publicacion.id}</span> : null}
                        </p>
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
                          <span className="truncate">
                            {pago.comprador?.nombre ?? '—'}
                            {pago.comprador ? ` · ${pago.comprador.usuario}` : ''}
                          </span>
                          {pago.cuentaEliminada ? <CuentaEliminada /> : null}
                        </div>
                      </div>
                      <Estado estado={pago.estado} />
                    </div>
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
                      <span className="font-semibold tabular-nums">{precio(pago.monto)}</span>
                      <span className="text-muted-foreground">Creado {fechaCorta(pago.creadoEn)}</span>
                      {pago.aprobadoEn ? (
                        <span className="text-muted-foreground">Aprobado {fechaCorta(pago.aprobadoEn)}</span>
                      ) : null}
                    </div>
                    <AccionesRecibo
                      pago={pago}
                      generando={generando}
                      descargando={descargando}
                      onVer={ver}
                      onDescargar={descargar}
                      onGenerar={generar}
                    />
                  </li>
                ))}
              </ul>

              <div className="hidden xl:block">
                <Table className="table-fixed">
                  <colgroup>
                    <col className="w-[22%]" />
                    <col className="w-[16%]" />
                    <col className="w-[9%]" />
                    <col className="w-[11%]" />
                    <col className="w-[10%]" />
                    <col className="w-[10%]" />
                    <col className="w-[22%]" />
                  </colgroup>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Publicación</TableHead>
                      <TableHead>Comprador</TableHead>
                      <TableHead className="text-right">Monto</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead>Creado</TableHead>
                      <TableHead>Aprobado</TableHead>
                      <TableHead className="text-right">Recibo</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {vacio ? (
                      <TableRow>
                        <TableCell colSpan={TOTAL_COLUMNAS} className="py-10 text-center text-muted-foreground">
                          Aún no hay pagos. Los pagos aparecen cuando un estudiante paga su publicación.
                        </TableCell>
                      </TableRow>
                    ) : null}
                    {datos.contenido.map((pago) => (
                      <TableRow key={pago.id}>
                        <TableCell>
                          <span className="block truncate font-medium" title={pago.publicacion?.titulo ?? 'Publicación eliminada'}>
                            {pago.publicacion?.titulo ?? 'Publicación eliminada'}
                          </span>
                          {pago.publicacion ? (
                            <span className="text-xs text-muted-foreground">#{pago.publicacion.id}</span>
                          ) : null}
                        </TableCell>
                        <TableCell>
                          <div className="min-w-0">
                            <p className="truncate font-medium" title={pago.comprador?.nombre}>{pago.comprador?.nombre ?? '—'}</p>
                            <p className="truncate text-xs text-muted-foreground" title={pago.comprador?.usuario}>
                              {pago.comprador?.usuario ?? ''}
                            </p>
                            {pago.cuentaEliminada ? <div className="mt-1"><CuentaEliminada /></div> : null}
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-right tabular-nums">{precio(pago.monto)}</TableCell>
                        <TableCell><Estado estado={pago.estado} /></TableCell>
                        <TableCell>
                          <span className="block truncate whitespace-nowrap text-xs text-muted-foreground" title={fecha(pago.creadoEn)}>
                            {fechaCorta(pago.creadoEn)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span className="block truncate whitespace-nowrap text-xs text-muted-foreground">
                            {pago.aprobadoEn ? fechaCorta(pago.aprobadoEn) : '—'}
                          </span>
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end text-right">
                            <AccionesRecibo
                              pago={pago}
                              generando={generando}
                              descargando={descargando}
                              onVer={ver}
                              onDescargar={descargar}
                              onGenerar={generar}
                            />
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </>
          ) : null}
          <div className="flex items-center justify-between border-t p-4">
            <Button variant="outline" size="sm" disabled={pagina <= 1} onClick={() => setPagina(pagina - 1)}>
              Anterior
            </Button>
            <span className="text-sm text-muted-foreground">Página {pagina} de {totalPaginas}</span>
            <Button variant="outline" size="sm" disabled={pagina >= totalPaginas} onClick={() => setPagina(pagina + 1)}>
              Siguiente
            </Button>
          </div>
        </CardContent>
      </Card>

      <DocumentoViewer documento={vista} onCerrar={() => setVista(null)} />
    </div>
  )
}
