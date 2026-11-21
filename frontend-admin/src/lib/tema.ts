import { useSyncExternalStore } from 'react'

/**
 * Tema del panel. Comparte la clave `upmarket.tema` con el sitio público y aplica la clase
 * `.dark` en <html>. Sin preferencia guardada se sigue la preferencia del sistema.
 */
export type Tema = 'claro' | 'oscuro'

const CLAVE = 'upmarket.tema'

const consulta =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null

let actual: Tema | null = null
let escuchandoSistema = false
const oyentes = new Set<() => void>()

function emitir(): void {
  for (const oyente of oyentes) oyente()
}

/** Preferencia guardada por la persona, o null si nunca eligió. */
export function leerPreferencia(): Tema | null {
  try {
    const valor = localStorage.getItem(CLAVE)
    if (valor === 'claro' || valor === 'oscuro') return valor
  } catch {
    /* almacenamiento no disponible */
  }
  return null
}

function temaDelSistema(): Tema {
  return consulta?.matches ? 'oscuro' : 'claro'
}

/** Tema efectivo con el que se debe pintar el panel. */
export function temaVigente(): Tema {
  if (!actual) actual = leerPreferencia() ?? temaDelSistema()
  return actual
}

function aplicar(tema: Tema): void {
  const raiz = document.documentElement
  raiz.classList.toggle('dark', tema === 'oscuro')
  raiz.dataset.tema = tema
  raiz.style.colorScheme = tema === 'oscuro' ? 'dark' : 'light'
}

function definir(tema: Tema): void {
  actual = tema
  aplicar(tema)
  emitir()
}

/** Se llama una vez, antes de montar React, para evitar el primer pintado con el tema equivocado. */
export function iniciarTema(): void {
  definir(temaVigente())
  if (escuchandoSistema) return
  escuchandoSistema = true
  consulta?.addEventListener('change', (evento) => {
    if (!leerPreferencia()) definir(evento.matches ? 'oscuro' : 'claro')
  })
}

/** Alterna claro ⇄ oscuro y guarda la elección explícita. */
export function alternarTema(): void {
  const siguiente: Tema = temaVigente() === 'oscuro' ? 'claro' : 'oscuro'
  try {
    localStorage.setItem(CLAVE, siguiente)
  } catch {
    /* almacenamiento no disponible: la elección vive solo en memoria */
  }
  definir(siguiente)
}

export function useTema(): Tema {
  return useSyncExternalStore(
    (oyente) => {
      oyentes.add(oyente)
      return () => {
        oyentes.delete(oyente)
      }
    },
    temaVigente,
    temaVigente,
  )
}
