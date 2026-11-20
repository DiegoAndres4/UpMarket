import { useEffect, useState, type FormEvent } from 'react'
import { Search, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import { fecha, numero, precio } from '../lib/formato'
import type { EstadoPago, EstadoPublicacion, Pagina, PublicacionAdmin } from '../lib/tipos'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../components/ui/alert-dialog'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Input } from '../components/ui/input'
import { Select } from '../components/ui/select'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10

type Filtros = {
  texto: string
  categoria: string
  estadoPublicacion: '' | EstadoPublicacion
  estadoPago: '' | EstadoPago
  vendedor: string
}

const VACIOS: Filtros = { texto: '', categoria: '', estadoPublicacion: '', estadoPago: '', vendedor: '' }

function Estado({ estado }: { estado: EstadoPublicacion }) {
  return <Badge variant={estado === 'DISPONIBLE' ? 'exito' : 'secondary'}>{estado === 'DISPONIBLE' ? 'Disponible' : 'Vendida'}</Badge>
}

function Pago({ estado }: { estado: EstadoPago }) {
  if (estado === 'APROBADO') return <Badge variant="exito">Aprobado</Badge>
  if (estado === 'PENDIENTE') return <Badge variant="alerta">Pendiente</Badge>
  return <Badge variant="destructive">Rechazado</Badge>
}

export default function Publicaciones() {
  const [borrador, setBorrador] = useState<Filtros>(VACIOS)
  const [consulta, setConsulta] = useState<Filtros & { pagina: number }>({ ...VACIOS, pagina: 1 })
  const [datos, setDatos] = useState<Pagina<PublicacionAdmin> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [porEliminar, setPorEliminar] = useState<PublicacionAdmin | null>(null)
  const [eliminando, setEliminando] = useState(false)

  useEffect(() => {
    let vigente = true
    const params = new URLSearchParams()
    if (consulta.texto.trim()) params.set('texto', consulta.texto.trim())
    if (consulta.categoria.trim()) params.set('categoria', consulta.categoria.trim())
    if (consulta.estadoPublicacion) params.set('estadoPublicacion', consulta.estadoPublicacion)
    if (consulta.estadoPago) params.set('estadoPago', consulta.estadoPago)
    if (consulta.vendedor.trim()) params.set('vendedor', consulta.vendedor.trim())
    params.set('pagina', String(consulta.pagina))
    params.set('tamano', String(TAMANO))

    setCargando(true)
    setError(null)
    api<Pagina<PublicacionAdmin>>(`/admin/publicaciones?${params.toString()}`)
      .then((pagina) => {
        if (vigente) setDatos(pagina)
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : 'No se pudieron cargar las publicaciones')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [consulta, recarga])

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault()
    setConsulta({ ...borrador, pagina: 1 })
  }

  const limpiar = () => {
    setBorrador(VACIOS)
    setConsulta({ ...VACIOS, pagina: 1 })
  }

  const eliminar = async () => {
    if (!porEliminar) return
    setEliminando(true)
    setError(null)
    try {
      await api(`/admin/publicaciones/${porEliminar.id}`, { method: 'DELETE' })
      const quedaban = datos?.contenido.length ?? 1
      setPorEliminar(null)
      if (quedaban <= 1 && consulta.pagina > 1) {
        setConsulta({ ...consulta, pagina: consulta.pagina - 1 })
      } else {
        setRecarga((valor) => valor + 1)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo eliminar la publicación')
    } finally {
      setEliminando(false)
    }
  }

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.total / datos.tamano)) : 1

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-bold">Publicaciones</h2>
        <p className="text-sm text-muted-foreground">
          Todas las publicaciones, incluidas las pendientes de pago y las vendidas.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Filtros</CardTitle>
          <CardDescription>Combina los filtros y pulsa Buscar.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={aplicar} className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Texto</span>
              <Input value={borrador.texto} onChange={(e) => setBorrador({ ...borrador, texto: e.target.value })}
                placeholder="Título o descripción" />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Categoría</span>
              <Input value={borrador.categoria} onChange={(e) => setBorrador({ ...borrador, categoria: e.target.value })}
                placeholder="Ej. Tecnología" />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Estado</span>
              <Select value={borrador.estadoPublicacion}
                onChange={(e) => setBorrador({ ...borrador, estadoPublicacion: e.target.value as Filtros['estadoPublicacion'] })}>
                <option value="">Todos</option>
                <option value="DISPONIBLE">Disponible</option>
                <option value="VENDIDA">Vendida</option>
              </Select>
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Pago</span>
              <Select value={borrador.estadoPago}
                onChange={(e) => setBorrador({ ...borrador, estadoPago: e.target.value as Filtros['estadoPago'] })}>
                <option value="">Todos</option>
                <option value="PENDIENTE">Pendiente</option>
                <option value="APROBADO">Aprobado</option>
                <option value="RECHAZADO">Rechazado</option>
              </Select>
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Vendedor</span>
              <Input value={borrador.vendedor} onChange={(e) => setBorrador({ ...borrador, vendedor: e.target.value })}
                placeholder="Usuario" />
            </label>
            <div className="flex gap-2 md:col-span-2 xl:col-span-5">
              <Button type="submit"><Search className="h-4 w-4" /> Buscar</Button>
              <Button type="button" variant="outline" onClick={limpiar}>Limpiar</Button>
            </div>
          </form>
        </CardContent>
      </Card>

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
            <CardTitle>Listado</CardTitle>
            <CardDescription>
              {datos ? `${numero(datos.total)} publicaciones · página ${datos.pagina} de ${totalPaginas}` : 'Cargando…'}
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
                  <TableHead>Vendedor</TableHead>
                  <TableHead>Categoría</TableHead>
                  <TableHead className="text-right">Precio</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Pago</TableHead>
                  <TableHead className="text-right">Vistas</TableHead>
                  <TableHead>Publicada</TableHead>
                  <TableHead className="text-right">Acciones</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {datos?.contenido.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={9} className="py-8 text-center text-muted-foreground">
                      No hay publicaciones con esos filtros.
                    </TableCell>
                  </TableRow>
                ) : null}
                {datos?.contenido.map((publicacion) => (
                  <TableRow key={publicacion.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        {publicacion.fotoUrl ? (
                          <img src={publicacion.fotoUrl} alt="" className="h-10 w-10 rounded-md border object-cover" />
                        ) : (
                          <div className="h-10 w-10 rounded-md border bg-muted" />
                        )}
                        <span className="max-w-[220px] truncate font-medium" title={publicacion.titulo}>
                          {publicacion.titulo}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="max-w-[150px] truncate" title={publicacion.vendedor}>{publicacion.vendedor}</TableCell>
                    <TableCell>{publicacion.categoria}</TableCell>
                    <TableCell className="whitespace-nowrap text-right">{precio(publicacion.precio)}</TableCell>
                    <TableCell><Estado estado={publicacion.estadoPublicacion} /></TableCell>
                    <TableCell><Pago estado={publicacion.estadoPago} /></TableCell>
                    <TableCell className="text-right">{numero(publicacion.vistas)}</TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{fecha(publicacion.publicadaEn)}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" aria-label={`Eliminar ${publicacion.titulo}`}
                        onClick={() => setPorEliminar(publicacion)}>
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <div className="flex items-center justify-between border-t p-4">
            <Button variant="outline" size="sm" disabled={consulta.pagina <= 1}
              onClick={() => setConsulta({ ...consulta, pagina: consulta.pagina - 1 })}>
              Anterior
            </Button>
            <span className="text-sm text-muted-foreground">Página {consulta.pagina} de {totalPaginas}</span>
            <Button variant="outline" size="sm" disabled={consulta.pagina >= totalPaginas}
              onClick={() => setConsulta({ ...consulta, pagina: consulta.pagina + 1 })}>
              Siguiente
            </Button>
          </div>
        </CardContent>
      </Card>

      <AlertDialog open={porEliminar !== null} onOpenChange={(abierto) => { if (!abierto) setPorEliminar(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>¿Eliminar la publicación?</AlertDialogTitle>
            <AlertDialogDescription>
              Se eliminará «{porEliminar?.titulo}» de {porEliminar?.vendedor}. Esta acción no se puede deshacer.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={eliminando}>Cancelar</AlertDialogCancel>
            <AlertDialogAction disabled={eliminando} onClick={() => void eliminar()}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              {eliminando ? 'Eliminando…' : 'Eliminar'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
