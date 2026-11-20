import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Une clases de Tailwind resolviendo conflictos, igual que el helper de shadcn/ui. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
