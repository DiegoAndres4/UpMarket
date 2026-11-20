import { useEffect, useState } from 'react'
import { Download, FileText } from 'lucide-react'
import { api, descargarDocumento } from '../lib/api'
import { fecha, numero, precio } from '../lib/formato'
import type { DocumentoAdmin, EstadoPago, Pagina, PagoAdmin } from '../lib/tipos'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10

function Estado({ estado }: { estado: EstadoPago }) {
  if (estado === 'APROBADO') return <Badge variant="exito">Aprobado</Badge>
  if (estado === 'PENDIENTE') return <Badge variant="alerta">Pendiente</Badge>
  return <Badge variant="destructive">Rechazado</Badge>
}

export default function Pagos() {
  const [pagina, setPagina] = useState(1)
  const [datos, setDatos] = useState<Pagina<PagoAdmin> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [generando, setGenerando] = useState<number | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

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
    try {
      await descargarDocumento({
        id: recibo.documentoId,
        nombreArchivo: recibo.nombreArchivo ?? 'recibo.pdf',
      })
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : 'No se pudo descargar el recibo')
    }
  }

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.total / datos.tamano)) : 1

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
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          {errorAccion}
        </p>
      ) : null}

      {error ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive">{error}</p>
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
            <div className="space-y-2 p-6">
              {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
            </div>
          ) : (
            <Table>
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
                {datos?.contenido.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                      Aún no hay pagos. Los pagos aparecen cuando un estudiante paga su publicación.
                    </TableCell>
                  </TableRow>
                ) : null}
                {datos?.contenido.map((pago) => (
                  <TableRow key={pago.id}>
                    <TableCell>
                      <span className="font-medium">{pago.publicacion?.titulo ?? 'Publicación eliminada'}</span>
                      {pago.publicacion ? (
                        <span className="ml-2 text-xs text-muted-foreground">#{pago.publicacion.id}</span>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <div className="font-medium">{pago.comprador?.nombre ?? '—'}</div>
                      <div className="max-w-[180px] truncate text-xs text-muted-foreground" title={pago.comprador?.usuario}>
                        {pago.comprador?.usuario ?? ''}
                      </div>
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-right">{precio(pago.monto)}</TableCell>
                    <TableCell><Estado estado={pago.estado} /></TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{fecha(pago.creadoEn)}</TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {pago.aprobadoEn ? fecha(pago.aprobadoEn) : '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      {pago.estado !== 'APROBADO' ? (
                        <span className="text-muted-foreground">—</span>
                      ) : pago.recibo.existe && pago.recibo.documentoId ? (
                        <div className="flex flex-col items-end gap-1">
                          <Button variant="outline" size="sm" onClick={() => void descargar(pago)}>
                            <Download className="h-4 w-4" /> Descargar recibo
                          </Button>
                          <span className="text-xs text-muted-foreground">{pago.recibo.numero}</span>
                        </div>
                      ) : (
                        <Button size="sm" disabled={generando === pago.id} onClick={() => void generar(pago)}>
                          <FileText className="h-4 w-4" />
                          {generando === pago.id ? 'Generando…' : 'Generar recibo'}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
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
    </div>
  )
}
