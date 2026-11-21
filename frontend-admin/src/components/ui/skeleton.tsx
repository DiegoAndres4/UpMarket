import { cn } from '../../lib/utils'

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} />
}

/** Filas de esqueleto con la forma de una lista o tabla (avatar, dos líneas y acciones). */
export function SkeletonFilas({
  filas = 5,
  conAvatar = false,
  className,
}: {
  filas?: number
  conAvatar?: boolean
  className?: string
}) {
  return (
    <div className={cn('space-y-3 p-4', className)} role="status" aria-label="Cargando contenido">
      {Array.from({ length: filas }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          {conAvatar ? (
            <Skeleton className="h-9 w-9 shrink-0 rounded-full" />
          ) : (
            <Skeleton className="h-10 w-10 shrink-0 rounded-md" />
          )}
          <div className="min-w-0 flex-1 space-y-1.5">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3 w-1/3" />
          </div>
          <Skeleton className="hidden h-6 w-16 shrink-0 sm:block" />
          <Skeleton className="h-8 w-8 shrink-0 rounded-md" />
        </div>
      ))}
    </div>
  )
}
