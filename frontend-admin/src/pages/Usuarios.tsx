import { useEffect, useState } from 'react'
import { Info, Mail } from 'lucide-react'
import { api, leerSesion } from '../lib/api'
import { numero } from '../lib/formato'
import type { Pagina, UsuarioAdmin, UsuarioAuthCore } from '../lib/tipos'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '../components/ui/alert-dialog'
import { Badge } from '../components/ui/badge'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10
const TOTAL_COLUMNAS = 8

function esAdmin(cuenta: UsuarioAuthCore): boolean {
  return cuenta.roles.some((rol) => String(rol).replace(/^ROLE_/, '') === 'ADMIN')
}

export default function Usuarios() {
  const [pagina, setPagina] = useState(1)
  const [datos, setDatos] = useState<Pagina<UsuarioAdmin> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [cuentas, setCuentas] = useState<Map<string, UsuarioAuthCore> | null>(null)
  const [errorCuentas, setErrorCuentas] = useState<string | null>(null)
  const [accionando, setAccionando] = useState<string | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [sesion] = useState(() => leerSesion())

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

  useEffect(() => {
    let vigente = true
    setErrorCuentas(null)
    api<UsuarioAuthCore[]>('/auth/users')
      .then((lista) => {
        if (!vigente) return
        const mapa = new Map<string, UsuarioAuthCore>()
        for (const cuenta of lista) mapa.set(cuenta.username, cuenta)
        setCuentas(mapa)
      })
      .catch((e: unknown) => {
        if (!vigente) return
        setCuentas(null)
        setErrorCuentas(e instanceof Error ? e.message : 'No se pudo cargar el estado de las cuentas')
      })
    return () => {
      vigente = false
    }
  }, [recarga])

  const cambiarBloqueo = async (cuenta: UsuarioAuthCore, bloquear: boolean) => {
    setErrorAccion(null)
    setAccionando(cuenta.username)
    try {
      const actualizada = await api<UsuarioAuthCore>(`/auth/users/${cuenta.id}/blocked`, {
        method: 'PATCH',
        body: JSON.stringify({ blocked: bloquear }),
      })
      setCuentas((previo) => {
        const siguiente = new Map<string, UsuarioAuthCore>(previo ?? [])
        siguiente.set(actualizada.username, actualizada)
        return siguiente
      })
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : 'No se pudo cambiar el estado de la cuenta')
    } finally {
      setAccionando(null)
    }
  }

  const puedeAccionar = (cuenta: UsuarioAuthCore | undefined): boolean => {
    if (!cuenta) return false
    return !esAdmin(cuenta) && cuenta.username !== sesion?.usuario
  }

  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.total / datos.tamano)) : 1

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-extrabold tracking-tight">Usuarios</h2>
        <p className="text-sm text-muted-foreground">
          Perfiles de la comunidad, estado de la cuenta y cuántas publicaciones tienen.
        </p>
      </header>

      {errorCuentas ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive">
              No se pudo cargar el estado de las cuentas de AuthCore: {errorCuentas}. La lista de perfiles sigue disponible.
            </p>
          </CardContent>
        </Card>
      ) : null}

      {errorAccion ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive">{errorAccion}</p>
          </CardContent>
        </Card>
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
                  <TableHead>Correo confirmado</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Cuenta</TableHead>
                  <TableHead className="text-right">Acción</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {datos?.contenido.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={TOTAL_COLUMNAS} className="py-8 text-center text-muted-foreground">
                      Todavía no hay perfiles.
                    </TableCell>
                  </TableRow>
                ) : null}
                {datos?.contenido.map((usuario) => {
                  const cuenta = cuentas?.get(usuario.usuario)
                  return (
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
                        {cuenta ? (
                          cuenta.emailVerified ? <Badge variant="exito">Sí</Badge> : <Badge variant="outline">No</Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell>
                        {cuenta ? (
                          cuenta.blocked ? <Badge variant="destructive">Bloqueado</Badge> : <Badge variant="exito">Activo</Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell>
                        {usuario.eliminada ? <Badge variant="destructive">Eliminada</Badge> : <Badge variant="exito">Activa</Badge>}
                      </TableCell>
                      <TableCell className="text-right">
                        {cuenta && puedeAccionar(cuenta) ? (
                          cuenta.blocked ? (
                            <AlertDialog>
                              <AlertDialogTrigger asChild>
                                <Button variant="outline" size="sm" disabled={accionando === cuenta.username}>
                                  Desbloquear
                                </Button>
                              </AlertDialogTrigger>
                              <AlertDialogContent>
                                <AlertDialogHeader>
                                  <AlertDialogTitle>¿Desbloquear a {usuario.nombre}?</AlertDialogTitle>
                                  <AlertDialogDescription>Podrá volver a iniciar sesión.</AlertDialogDescription>
                                </AlertDialogHeader>
                                <AlertDialogFooter>
                                  <AlertDialogCancel>Cancelar</AlertDialogCancel>
                                  <AlertDialogAction
                                    onClick={() => cambiarBloqueo(cuenta, false)}
                                    disabled={accionando === cuenta.username}
                                  >
                                    Desbloquear
                                  </AlertDialogAction>
                                </AlertDialogFooter>
                              </AlertDialogContent>
                            </AlertDialog>
                          ) : (
                            <AlertDialog>
                              <AlertDialogTrigger asChild>
                                <Button variant="outline" size="sm" disabled={accionando === cuenta.username}>
                                  Bloquear
                                </Button>
                              </AlertDialogTrigger>
                              <AlertDialogContent>
                                <AlertDialogHeader>
                                  <AlertDialogTitle>¿Bloquear a {usuario.nombre}?</AlertDialogTitle>
                                  <AlertDialogDescription>
                                    No podrá volver a iniciar sesión. Si ya tiene una sesión abierta, seguirá activa hasta 1 hora.
                                  </AlertDialogDescription>
                                </AlertDialogHeader>
                                <AlertDialogFooter>
                                  <AlertDialogCancel>Cancelar</AlertDialogCancel>
                                  <AlertDialogAction
                                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                                    onClick={() => cambiarBloqueo(cuenta, true)}
                                    disabled={accionando === cuenta.username}
                                  >
                                    Bloquear
                                  </AlertDialogAction>
                                </AlertDialogFooter>
                              </AlertDialogContent>
                            </AlertDialog>
                          )
                        ) : null}
                      </TableCell>
                    </TableRow>
                  )
                })}
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
