import { cn } from '../../lib/utils'

/** Indicador circular de espera. Es decorativo: el verbo del botón aporta el significado. */
export function Spinner({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'inline-block h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent',
        className,
      )}
    />
  )
}
