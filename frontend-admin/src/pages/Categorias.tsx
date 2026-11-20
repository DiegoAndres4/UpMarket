import { useEffect, useState, type FormEvent } from 'react'
import { Tags } from 'lucide-react'
import { api, ErrorApi } from '../lib/api'
import { numero } from '../lib/formato'
import { Button } from '../components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Input } from '../components/ui/input'
import { Skeleton } from '../components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table'

type Categoria = {
  nombre: string
  disponibles: number
}

export default function Categorias() {
  const [categorias, setCategorias] = useState<Categoria[] | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [nombre, setNombre] = useState('')
  const [creando, setCreando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const [errorFormulario, setErrorFormulario] = useState<string | null>(null)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    api<Categoria[]>('/categorias')
      .then((resultado) => {
        if (vigente) setCategorias(resultado)
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : 'No se pudieron cargar las categorías')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [recarga])

  const crear = async (evento: FormEvent) => {
    evento.preventDefault()
    if (creando) return
    const valor = nombre.trim().replace(/\s+/g, ' ')
    setErrorFormulario(null)
    setAviso(null)
    if (valor.length < 2) {
      setErrorFormulario('Escribe un nombre de al menos 2 caracteres.')
      return
    }
    setCreando(true)
    try {
      await api<Categoria>('/categorias', { method: 'POST', body: JSON.stringify({ nombre: valor }) })
      setNombre('')
      setAviso('Categoría creada')
      setRecarga((actual) => actual + 1)
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 409) {
        setErrorFormulario('Esa categoría ya existe.')
      } else {
        setErrorFormulario(e instanceof Error ? e.message : 'No se pudo crear la categoría')
      }
    } finally {
      setCreando(false)
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-extrabold tracking-tight">Categorías</h2>
        <p className="text-sm text-muted-foreground">
          Administra las categorías del catálogo. Los estudiantes ya no crean categorías desde la aplicación.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Agregar categoría</CardTitle>
          <CardDescription>El nombre se normaliza: mayúsculas y tildes no crean duplicados.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={(e) => void crear(e)} noValidate>
            <div className="flex-1 space-y-1">
              <label htmlFor="categoria-nombre" className="text-sm font-medium">Nombre de la categoría</label>
              <Input
                id="categoria-nombre"
                value={nombre}
                maxLength={60}
                onChange={(e) => { setNombre(e.target.value); setErrorFormulario(null) }}
                placeholder="Deportes"
                aria-invalid={errorFormulario ? true : undefined}
                aria-describedby={errorFormulario ? 'categoria-error' : undefined}
              />
            </div>
            <Button type="submit" disabled={creando}>
              <Tags className="h-4 w-4" /> {creando ? 'Agregando…' : 'Agregar categoría'}
            </Button>
          </form>
          {errorFormulario ? <p id="categoria-error" className="mt-3 text-sm text-destructive" role="alert">{errorFormulario}</p> : null}
          {aviso ? <p className="mt-3 text-sm" role="status">{aviso}</p> : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Categorías existentes</CardTitle>
          <CardDescription>
            {categorias ? `${numero(categorias.length)} categorías en el catálogo` : 'Cargando…'}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          {cargando ? (
            <div className="space-y-2 p-6">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
            </div>
          ) : error ? (
            <div className="space-y-3 p-6">
              <p className="text-sm text-destructive" role="alert">{error}</p>
              <Button variant="outline" size="sm" onClick={() => setRecarga((actual) => actual + 1)}>
                Reintentar
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Categoría</TableHead>
                  <TableHead className="text-right">Publicaciones disponibles</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {categorias?.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={2} className="py-10 text-center text-muted-foreground">
                      Todavía no hay categorías. Agrega la primera con el formulario de arriba.
                    </TableCell>
                  </TableRow>
                ) : null}
                {categorias?.map((categoria) => (
                  <TableRow key={categoria.nombre}>
                    <TableCell className="font-medium">{categoria.nombre}</TableCell>
                    <TableCell className="text-right tabular-nums">{numero(categoria.disponibles)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
