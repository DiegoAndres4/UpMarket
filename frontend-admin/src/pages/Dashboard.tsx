import { useEffect, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../lib/api'
import { numero, precio } from '../lib/formato'
import type { Estadisticas } from '../lib/tipos'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const COLORES = ['#2563eb', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#64748b']

function Cifra({ titulo, valor, detalle }: { titulo: string; valor: string; detalle?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{titulo}</CardDescription>
        <CardTitle className="text-2xl">{valor}</CardTitle>
      </CardHeader>
      {detalle ? (
        <CardContent>
          <p className="text-xs text-muted-foreground">{detalle}</p>
        </CardContent>
      ) : null}
    </Card>
  )
}

export default function Dashboard() {
  const [datos, setDatos] = useState<Estadisticas | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

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
        <h2 className="text-2xl font-bold">Tablero</h2>
        <p className="text-sm text-muted-foreground">Estado general de la comunidad en tiempo real.</p>
      </header>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Cifras principales">
        <Cifra titulo="Publicaciones" valor={numero(datos.totalPublicaciones)}
          detalle={`${numero(datos.publicacionesPorEstado.DISPONIBLE)} disponibles · ${numero(datos.publicacionesPorEstado.VENDIDA)} vendidas`} />
        <Cifra titulo="Vistas acumuladas" valor={numero(datos.vistasTotales)} />
        <Cifra titulo="Perfiles" valor={numero(datos.totalPerfiles)} detalle={`${numero(datos.totalResenas)} reseñas`} />
        <Cifra titulo="Pagos aprobados" valor={numero(datos.pagosAprobados)}
          detalle={`${numero(pagos.PENDIENTE)} pendientes · ${numero(pagos.RECHAZADO)} rechazados`} />
        <Cifra titulo="Ingresos simulados" valor={precio(datos.ingresosAprobados)} detalle="Suma de pagos aprobados" />
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
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis dataKey="fecha" tickFormatter={(fecha: string) => fecha.slice(5)} fontSize={12} />
                <YAxis allowDecimals={false} fontSize={12} />
                <Tooltip
                  contentStyle={{
                    background: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: 8,
                  }}
                  labelFormatter={(fecha) => `Fecha: ${fecha}`}
                />
                <Bar dataKey="total" name="Publicaciones" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
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
                <PieChart>
                  <Pie data={categorias} dataKey="total" nameKey="nombre" innerRadius={55} outerRadius={95} paddingAngle={2}>
                    {categorias.map((categoria, indice) => (
                      <Cell key={categoria.nombre} fill={COLORES[indice % COLORES.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      background: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: 8,
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            )}
            <div className="mt-2 flex flex-wrap gap-2">
              {categorias.map((categoria, indice) => (
                <span key={categoria.nombre} className="flex items-center gap-1 text-xs text-muted-foreground">
                  <span className="h-2 w-2 rounded-full" style={{ background: COLORES[indice % COLORES.length] }} />
                  {categoria.nombre} ({categoria.total})
                </span>
              ))}
            </div>
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
                    <span className="mr-2 text-muted-foreground">#{indice + 1}</span>
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
    </div>
  )
}
