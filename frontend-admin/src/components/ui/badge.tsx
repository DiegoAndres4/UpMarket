import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/utils'

/*
 * Insignias de estado con la paleta de la marca: ámbar con tinta para lo disponible,
 * tinta con blanco para lo vendido y rojo con blanco para lo pendiente de pago.
 */
const variantes = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        destructive: 'border-transparent bg-destructive text-destructive-foreground',
        outline: 'text-foreground',
        exito: 'border-transparent bg-primary text-primary-foreground',
        alerta: 'border-transparent bg-destructive text-destructive-foreground',
        tinta: 'border-transparent bg-foreground text-background',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof variantes> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(variantes({ variant }), className)} {...props} />
}
