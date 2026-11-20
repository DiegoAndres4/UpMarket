export type EstadoPublicacion = 'DISPONIBLE' | 'VENDIDA'
export type EstadoPago = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'

export type Pagina<T> = {
  contenido: T[]
  pagina: number
  tamano: number
  total: number
}

export type PublicacionAdmin = {
  id: number
  titulo: string
  vendedor: string
  categoria: string
  precio: number
  estadoPublicacion: EstadoPublicacion
  estadoPago: EstadoPago
  vistas: number
  publicadaEn: string
  fotoUrl: string | null
}

export type UsuarioAdmin = {
  usuario: string
  nombre: string
  correo: string
  fotoUrl: string | null
  eliminada: boolean
  publicaciones: number
}

/** Cuenta tal como la devuelve AuthCore (el username es el correo). */
export type UsuarioAuthCore = {
  id: number
  username: string
  roles: string[]
  emailVerified: boolean
  blocked: boolean
}

export type Estadisticas = {
  totalPublicaciones: number
  publicacionesPorEstado: Record<EstadoPublicacion, number>
  publicacionesPorEstadoPago: Record<EstadoPago, number>
  pagosAprobados: number
  ingresosAprobados: number
  vistasTotales: number
  topPublicaciones: { id: number; titulo: string; vistas: number }[]
  publicacionesPorCategoria: Record<string, number>
  publicacionesPorDia: { fecha: string; total: number }[]
  totalPerfiles: number
  totalResenas: number
}
