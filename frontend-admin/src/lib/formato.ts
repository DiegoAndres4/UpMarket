const PESOS = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

const FECHA = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const FECHA_CORTA = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' })

const NUMERO = new Intl.NumberFormat('es-CO')

export const precio = (valor: number): string => PESOS.format(valor)

export const fecha = (iso: string): string => FECHA.format(new Date(iso))

/** Fecha sin hora para columnas estrechas del panel. */
export const fechaCorta = (iso: string): string => FECHA_CORTA.format(new Date(iso))

export const numero = (valor: number): string => NUMERO.format(valor)
