const PESOS = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

const FECHA = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const NUMERO = new Intl.NumberFormat('es-CO')

export const precio = (valor: number): string => PESOS.format(valor)

export const fecha = (iso: string): string => FECHA.format(new Date(iso))

export const numero = (valor: number): string => NUMERO.format(valor)
