import { useEffect, useState } from 'react'
import { Info, Mail, ShieldAlert } from 'lucide-react'
import { api } from '../lib/api'
import { numero } from '../lib/formato'
import type { Pagina, UsuarioAdmin } from '../lib/tipos'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10

export default function Usuarios() {
  const [pagina, setPagina] = useState(1)
  const [datos, setDatos] = useState<Pagina<UsuarioAdmin> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    api<Pagina<UsuarioAdmin>>(`/admin/usuarios?pagina=${pagina}&tamano=${TAMANO}`)
      .then((resultado) => {
        if (vigente) setDatos(resultado)
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : 'No se pudieron cargar los usuarios')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [pagina, recarga])

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.total / datos.tamano)) : 1

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-extrabold tracking-tight">Usuarios</h2>
        <p className="text-sm text-muted-foreground">Perfiles de la comunidad y cuántas publicaciones tienen.</p>
      </header>

      <Card className="bg-muted">
        <CardHeader className="flex-row gap-3 space-y-0">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
          <div>
            <CardTitle>Solo lectura por ahora</CardTitle>
            <CardDescription>
              El bloqueo de cuentas vive en AuthCore y todavía no está expuesto. Cuando lo esté, se habilitará aquí.
            </CardDescription>
          </div>
        </CardHeader>
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
        <CardHeader>
          <CardTitle>Perfiles</CardTitle>
          <CardDescription>
            {datos ? `${numero(datos.total)} usuarios · página ${datos.pagina} de ${totalPaginas}` : 'Cargando…'}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {cargando ? (
            <div className="space-y-2 p-6">
              {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Usuario</TableHead>
                  <TableHead>Nombre</TableHead>
                  <TableHead>Correo</TableHead>
                  <TableHead className="text-right">Publicaciones</TableHead>
                  <TableHead>Cuenta</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {datos?.contenido.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                      Todavía no hay perfiles.
                    </TableCell>
                  </TableRow>
                ) : null}
                {datos?.contenido.map((usuario) => (
                  <TableRow key={usuario.usuario}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        {usuario.fotoUrl ? (
                          <img src={usuario.fotoUrl} alt="" className="h-9 w-9 rounded-full border object-cover" />
                        ) : (
                          <div className="flex h-9 w-9 items-center justify-center rounded-full border bg-muted text-xs font-semibold uppercase">
                            {usuario.nombre.slice(0, 1)}
                          </div>
                        )}
                        <span className="max-w-[200px] truncate" title={usuario.usuario}>{usuario.usuario}</span>
                      </div>
                    </TableCell>
                    <TableCell>{usuario.nombre}</TableCell>
                    <TableCell>
                      <span className="flex items-center gap-1 text-muted-foreground">
                        <Mail className="h-3.5 w-3.5" /> {usuario.correo}
                      </span>
                    </TableCell>
                    <TableCell className="text-right">{numero(usuario.publicaciones)}</TableCell>
                    <TableCell>
                      {usuario.eliminada ? <Badge variant="destructive">Eliminada</Badge> : <Badge variant="exito">Activa</Badge>}
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
            <span className="flex items-center gap-1 text-sm text-muted-foreground">
              <Info className="h-3.5 w-3.5" /> Página {pagina} de {totalPaginas}
            </span>
            <Button variant="outline" size="sm" disabled={pagina >= totalPaginas} onClick={() => setPagina(pagina + 1)}>
              Siguiente
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
