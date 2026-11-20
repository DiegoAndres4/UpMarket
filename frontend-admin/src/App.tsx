import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { leerSesion, salir } from './lib/api'
import Dashboard from './pages/Dashboard'
import Publicaciones from './pages/Publicaciones'
import Usuarios from './pages/Usuarios'

export default function App() {
  const [sesion] = useState(() => leerSesion())

  useEffect(() => {
    if (!sesion?.esAdmin) {
      window.location.replace('/')
    }
  }, [sesion])

  if (!sesion?.esAdmin) return null

  return (
    <BrowserRouter basename="/admin">
      <Routes>
        <Route element={<Layout usuario={sesion.usuario} onSalir={salir} />}>
          <Route index element={<Dashboard />} />
          <Route path="publicaciones" element={<Publicaciones />} />
          <Route path="usuarios" element={<Usuarios />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
