/** Cliente HTTP del panel. Reutiliza el token que el sitio público guarda en localStorage. */

export type Sesion = {
  token: string
  usuario: string
  roles: string[]
  esAdmin: boolean
}

const CLAVE_TOKEN = 'upmarket.token'

function decodificarBase64Url(texto: string): string {
  const normalizado = texto.replace(/-/g, '+').replace(/_/g, '/')
  const relleno = normalizado.padEnd(Math.ceil(normalizado.length / 4) * 4, '=')
  const binario = atob(relleno)
  const bytes = Uint8Array.from(binario, (caracter) => caracter.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** Lee la sesión del mismo token JWT que usa `frontend/js/app.js`; no hay sesión propia del panel. */
export function leerSesion(): Sesion | null {
  const token = localStorage.getItem(CLAVE_TOKEN)
  if (!token) return null
  try {
    const partes = token.split('.')
    if (partes.length < 2) return null
    const datos = JSON.parse(decodificarBase64Url(partes[1])) as {
      sub?: string
      roles?: string[]
      exp?: number
    }
    if (datos.exp && datos.exp * 1000 < Date.now()) {
      localStorage.removeItem(CLAVE_TOKEN)
      return null
    }
    if (!datos.sub) return null
    const roles = (datos.roles ?? []).map((rol) => String(rol).replace(/^ROLE_/, ''))
    return { token, usuario: datos.sub, roles, esAdmin: roles.includes('ADMIN') }
  } catch {
    localStorage.removeItem(CLAVE_TOKEN)
    return null
  }
}

export function salir(): void {
  localStorage.removeItem(CLAVE_TOKEN)
  window.location.replace('/')
}

/** Error con el mensaje que ya redactó el backend. */
export class ErrorApi extends Error {
  constructor(mensaje: string, readonly estado: number, readonly codigo?: string) {
    super(mensaje)
    this.name = 'ErrorApi'
  }
}

export type DocumentoDescargable = {
  id: number
  nombreArchivo: string
}

/**
 * Descarga un documento del panel con el token en la cabecera, nunca en la URL.
 * En Cloudinary el backend responde un JSON con un enlace firmado; en local, el archivo.
 */
export async function descargarDocumento(documento: DocumentoDescargable): Promise<void> {
  const sesion = leerSesion()
  if (!sesion) {
    salir()
    throw new ErrorApi('La sesión no es válida', 401)
  }
  const respuesta = await fetch(`/api/admin/documentos/${documento.id}/descarga`, {
    headers: {
      Authorization: `Bearer ${sesion.token}`,
      Accept: 'application/json',
    },
  })
  if (respuesta.status === 401) {
    salir()
    throw new ErrorApi('La sesión venció; vuelve a iniciar sesión', 401)
  }
  if (!respuesta.ok) {
    const texto = await respuesta.text()
    let mensaje = 'No se pudo descargar el documento'
    try {
      const cuerpo = JSON.parse(texto) as { mensaje?: string; message?: string; error?: string }
      mensaje = cuerpo.mensaje ?? cuerpo.message ?? cuerpo.error ?? mensaje
    } catch {
      // respuesta sin JSON
    }
    throw new ErrorApi(mensaje, respuesta.status)
  }

  const tipo = respuesta.headers.get('content-type') ?? ''
  const enlace = document.createElement('a')
  enlace.rel = 'noopener'
  if (tipo.includes('application/json')) {
    const cuerpo = (await respuesta.json()) as { enlace?: string }
    if (!cuerpo.enlace) {
      throw new ErrorApi('El servidor no devolvió el enlace del documento', 500)
    }
    enlace.href = cuerpo.enlace
    enlace.target = '_blank'
  } else {
    const blob = await respuesta.blob()
    enlace.href = URL.createObjectURL(blob)
    enlace.download = documento.nombreArchivo
    const url = enlace.href
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }
  enlace.click()
}

export async function api<T>(ruta: string, init: RequestInit = {}): Promise<T> {
  const sesion = leerSesion()
  if (!sesion) {
    salir()
    throw new ErrorApi('La sesión no es válida', 401)
  }
  const respuesta = await fetch(`/api${ruta}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${sesion.token}`,
      ...(init.headers ?? {}),
    },
  })
  if (respuesta.status === 401) {
    salir()
    throw new ErrorApi('La sesión venció; vuelve a iniciar sesión', 401)
  }
  const texto = await respuesta.text()
  let datos: unknown = null
  if (texto) {
    try {
      datos = JSON.parse(texto)
    } catch {
      datos = null
    }
  }
  if (!respuesta.ok) {
    const cuerpo = datos as { mensaje?: string; error?: string; message?: string; code?: string } | null
    const mensaje = cuerpo?.mensaje ?? cuerpo?.message ?? cuerpo?.error ?? 'No se pudo completar la acción'
    throw new ErrorApi(mensaje, respuesta.status, cuerpo?.code)
  }
  return datos as T
}
