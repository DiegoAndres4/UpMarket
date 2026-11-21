import { useEffect, useState } from 'react'
import { Info, Lock, LockOpen } from 'lucide-react'
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
import { SkeletonFilas } from '../components/ui/skeleton'
import { Spinner } from '../components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

const TAMANO = 10
const TOTAL_COLUMNAS = 4

function esAdmin(cuenta: UsuarioAuthCore): boolean {
  return cuenta.roles.some((rol) => String(rol).replace(/^ROLE_/, '') === 'ADMIN')
}

function Avatar({ usuario }: { usuario: UsuarioAdmin }) {
  if (usuario.fotoUrl) {
    return <img src={usuario.fotoUrl} alt="" className="h-9 w-9 shrink-0 rounded-full border object-cover" />
  }
  return (
    <div
      aria-hidden="true"
      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border bg-muted text-xs font-semibold uppercase"
    >
      {usuario.nombre.slice(0, 1)}
    </div>
  )
}

/** Estado de la cuenta en una sola columna: actividad, correo confirmado y baja del perfil. */
function EstadoCuenta({
  usuario,
  cuenta,
  horizontal = false,
}: {
  usuario: UsuarioAdmin
  cuenta: UsuarioAuthCore | undefined
  horizontal?: boolean
}) {
  return (
    <div className={horizontal ? 'flex flex-wrap items-center gap-1' : 'flex flex-col items-start gap-1'}>
      {cuenta ? (
        cuenta.blocked ? (
          <Badge variant="destructive" title="Estado de la cuenta">Bloqueado</Badge>
        ) : (
          <Badge variant="exito" title="Estado de la cuenta">Activo</Badge>
        )
      ) : (
        <span className="text-muted-foreground">—</span>
      )}
      {cuenta ? (
        cuenta.emailVerified ? (
          <Badge variant="secondary" title="Correo confirmado">Correo confirmado</Badge>
        ) : (
          <Badge variant="outline" title="Correo sin confirmar">Correo sin confirmar</Badge>
        )
      ) : null}
      {usuario.eliminada ? <Badge variant="destructive">Cuenta eliminada</Badge> : null}
    </div>
  )
}

function AccionCuenta({
  usuario,
  cuenta,
  accionando,
  onCambiar,
}: {
  usuario: UsuarioAdmin
  cuenta: UsuarioAuthCore | undefined
  accionando: string | null
  onCambiar: (cuenta: UsuarioAuthCore, bloquear: boolean) => Promise<void>
}) {
  if (!cuenta) return null
  const pendiente = accionando === cuenta.username
  const bloquear = !cuenta.blocked
  const verbo = bloquear ? 'Bloquear' : 'Desbloquear'
  const Icono = bloquear ? Lock : LockOpen

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          className="h-8 w-8"
          disabled={pendiente}
          aria-label={`${verbo} la cuenta de ${usuario.nombre}`}
          title={verbo}
        >
          {pendiente ? <Spinner /> : <Icono className="h-4 w-4" aria-hidden="true" />}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>¿{verbo} a {usuario.nombre}?</AlertDialogTitle>
          <AlertDialogDescription>
            {bloquear
              ? 'No podrá volver a iniciar sesión. Si ya tiene una sesión abierta, seguirá activa hasta 1 hora.'
              : 'Podrá volver a iniciar sesión.'}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pendiente}>Cancelar</AlertDialogCancel>
          <AlertDialogAction
            className={bloquear ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90' : undefined}
            disabled={pendiente}
            onClick={() => void onCambiar(cuenta, bloquear)}
          >
            {pendiente ? (
              <>
                <Spinner /> {bloquear ? 'Bloqueando…' : 'Desbloqueando…'}
              </>
            ) : (
              verbo
            )}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
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
            <p className="text-sm text-destructive-text">
              No se pudo cargar el estado de las cuentas de AuthCore: {errorCuentas}. La lista de perfiles sigue disponible.
            </p>
          </CardContent>
        </Card>
      ) : null}

      {errorAccion ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive-text">{errorAccion}</p>
          </CardContent>
        </Card>
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
        <CardHeader>
          <CardTitle>Perfiles</CardTitle>
          <CardDescription>
            {datos ? `${numero(datos.total)} usuarios · página ${datos.pagina} de ${totalPaginas}` : 'Cargando…'}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {cargando ? (
            <SkeletonFilas filas={6} conAvatar />
          ) : datos ? (
            <>
              <ul className="divide-y md:hidden">
                {datos.contenido.length === 0 ? (
                  <li className="px-4 py-8 text-center text-muted-foreground">Todavía no hay perfiles.</li>
                ) : null}
                {datos.contenido.map((usuario) => {
                  const cuenta = cuentas?.get(usuario.usuario)
                  return (
                    <li key={usuario.usuario} className="flex items-start gap-3 p-4">
                      <Avatar usuario={usuario} />
                      <div className="min-w-0 flex-1 space-y-2">
                        <div className="min-w-0">
                          <p className="truncate font-medium" title={usuario.nombre}>{usuario.nombre}</p>
                          <p className="truncate text-sm text-muted-foreground" title={usuario.correo}>{usuario.correo}</p>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <EstadoCuenta usuario={usuario} cuenta={cuenta} horizontal />
                          <span className="text-xs text-muted-foreground">
                            {numero(usuario.publicaciones)} {usuario.publicaciones === 1 ? 'publicación' : 'publicaciones'}
                          </span>
                        </div>
                      </div>
                      <div className="shrink-0">
                        <AccionCuenta
                          usuario={usuario}
                          cuenta={puedeAccionar(cuenta) ? cuenta : undefined}
                          accionando={accionando}
                          onCambiar={cambiarBloqueo}
                        />
                      </div>
                    </li>
                  )
                })}
              </ul>

              <div className="hidden md:block">
                <Table className="table-fixed">
                  <colgroup>
                    <col className="w-[36%]" />
                    <col className="w-[39%]" />
                    <col className="w-[14%]" />
                    <col className="w-[11%]" />
                  </colgroup>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Usuario</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead className="text-right">
                        <span className="sr-only">Publicaciones</span>
                        <span aria-hidden="true" title="Publicaciones">Publ.</span>
                      </TableHead>
                      <TableHead className="text-right">
                        <span className="sr-only">Acciones</span>
                        <span aria-hidden="true" title="Acciones">Acc.</span>
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {datos.contenido.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={TOTAL_COLUMNAS} className="py-8 text-center text-muted-foreground">
                          Todavía no hay perfiles.
                        </TableCell>
                      </TableRow>
                    ) : null}
                    {datos.contenido.map((usuario) => {
                      const cuenta = cuentas?.get(usuario.usuario)
                      return (
                        <TableRow key={usuario.usuario}>
                          <TableCell>
                            <div className="flex min-w-0 items-center gap-3">
                              <Avatar usuario={usuario} />
                              <div className="min-w-0">
                                <p className="truncate font-medium" title={usuario.nombre}>{usuario.nombre}</p>
                                <p className="truncate text-xs text-muted-foreground" title={usuario.correo}>{usuario.correo}</p>
                              </div>
                            </div>
                          </TableCell>
                          <TableCell>
                            <EstadoCuenta usuario={usuario} cuenta={cuenta} />
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{numero(usuario.publicaciones)}</TableCell>
                          <TableCell>
                            <div className="flex justify-end">
                              <AccionCuenta
                                usuario={usuario}
                                cuenta={puedeAccionar(cuenta) ? cuenta : undefined}
                                accionando={accionando}
                                onCambiar={cambiarBloqueo}
                              />
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            </>
          ) : null}
          <div className="flex items-center justify-between border-t p-4">
            <Button variant="outline" size="sm" disabled={pagina <= 1} onClick={() => setPagina(pagina - 1)}>
              Anterior
            </Button>
            <span className="flex items-center gap-1 text-sm text-muted-foreground">
              <Info className="h-3.5 w-3.5" aria-hidden="true" /> Página {pagina} de {totalPaginas}
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
