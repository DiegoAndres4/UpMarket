import { NavLink, Outlet } from 'react-router-dom'
import { BarChart3, CreditCard, ExternalLink, LogOut, Package, Users } from 'lucide-react'
import { cn } from '../lib/utils'
import { Button } from './ui/button'

const enlaces = [
  { a: '/', texto: 'Tablero', Icono: BarChart3, fin: true },
  { a: '/publicaciones', texto: 'Publicaciones', Icono: Package, fin: false },
  { a: '/pagos', texto: 'Pagos', Icono: CreditCard, fin: false },
  { a: '/usuarios', texto: 'Usuarios', Icono: Users, fin: false },
]

export function Layout({ usuario, onSalir }: { usuario: string; onSalir: () => void }) {
  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 md:flex-row md:px-6">
        <aside className="md:w-60 md:shrink-0">
          <div className="mb-6 flex items-start justify-between gap-3 md:block">
            <div>
              <h1 className="text-[1.4rem] font-extrabold leading-none tracking-[-0.02em]">
                UP<span className="text-brand">Market</span>
              </h1>
              <p className="mt-1.5 text-sm text-muted-foreground">Administración</p>
            </div>
            <Button variant="ghost" size="sm" className="md:hidden" onClick={onSalir}>
              <LogOut className="h-4 w-4" /> Salir
            </Button>
          </div>

          <nav className="flex gap-1 overflow-x-auto md:flex-col" aria-label="Secciones del panel">
            {enlaces.map(({ a, texto, Icono, fin }) => (
              <NavLink
                key={a}
                to={a}
                end={fin}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors',
                    isActive
                      ? 'bg-secondary font-semibold text-secondary-foreground'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <Icono className="h-4 w-4" />
                {texto}
              </NavLink>
            ))}
          </nav>

          <div className="mt-6 space-y-2 border-t pt-4">
            <a
              href="/"
              className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <ExternalLink className="h-4 w-4" /> Volver al sitio
            </a>
            <div className="px-3 py-2">
              <p className="truncate text-xs text-muted-foreground" title={usuario}>
                {usuario}
              </p>
              <Button variant="ghost" size="sm" className="mt-2 w-full justify-start" onClick={onSalir}>
                <LogOut className="h-4 w-4" /> Cerrar sesión
              </Button>
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
