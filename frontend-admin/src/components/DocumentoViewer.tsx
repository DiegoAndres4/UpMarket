import { useEffect, useState } from 'react'
import { Download, ExternalLink } from 'lucide-react'
import { contenidoDocumento, descargarDocumento } from '../lib/api'
import { Button } from './ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './ui/dialog'
import { Spinner } from './ui/spinner'

/** Documento que el panel puede mostrar en línea, sin descargarlo. Solo los PDF se previsualizan. */
export type DocumentoVista = {
  id: number
  numero: string
  formato: 'PDF'
  nombreArchivo: string
  tipo: string
}

type Estado = 'cargando' | 'listo' | 'error'

/**
 * Diálogo de vista previa: trae los bytes con el token en la cabecera, crea un Blob URL y lo revoca
 * al cerrar. El PDF va en un iframe; el XML solo se descarga, no se previsualiza.
 */
export function DocumentoViewer({ documento, onCerrar }: {
  documento: DocumentoVista | null
  onCerrar: () => void
}) {
  const [estado, setEstado] = useState<Estado>('cargando')
  const [url, setUrl] = useState<string | null>(null)
  const [intento, setIntento] = useState(0)
  const [descargando, setDescargando] = useState(false)
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)

  useEffect(() => {
    if (!documento) {
      setEstado('cargando')
      setUrl(null)
      setErrorDescarga(null)
      return
    }
    let vigente = true
    let creada: string | null = null
    setEstado('cargando')
    setUrl(null)

    contenidoDocumento(documento)
      .then((blob) => {
        const objectUrl = URL.createObjectURL(blob)
        creada = objectUrl
        if (!vigente) return
        setUrl(objectUrl)
        setEstado('listo')
      })
      .catch(() => {
        if (vigente) setEstado('error')
      })

    return () => {
      vigente = false
      if (creada) URL.revokeObjectURL(creada)
    }
  }, [documento, intento])

  const descargar = async () => {
    if (!documento || descargando) return
    setDescargando(true)
    setErrorDescarga(null)
    try {
      await descargarDocumento({ id: documento.id, nombreArchivo: documento.nombreArchivo })
    } catch (e) {
      setErrorDescarga(e instanceof Error ? e.message : 'No se pudo descargar el documento')
    } finally {
      setDescargando(false)
    }
  }

  const abrirEnPestana = () => {
    if (url) window.open(url, '_blank', 'noopener,noreferrer')
  }

  return (
    <Dialog open={documento !== null} onOpenChange={(abierto) => { if (!abierto) onCerrar() }}>
      {documento ? (
        <DialogContent className="flex h-[100dvh] w-screen max-w-none flex-col gap-0 overflow-hidden rounded-none p-0 sm:h-[90vh] sm:w-[90vw] sm:max-w-[90vw] sm:rounded-lg">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3 pr-12">
            <div className="min-w-0">
              <DialogTitle className="truncate text-base">
                {documento.numero} · {documento.tipo}
              </DialogTitle>
              <DialogDescription className="text-xs">
                Vista previa del documento generado. No hace falta descargarlo.
              </DialogDescription>
              {errorDescarga ? (
                <p className="text-xs text-destructive-text" role="alert">{errorDescarga}</p>
              ) : null}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" size="sm" onClick={abrirEnPestana} disabled={estado !== 'listo' || !url}>
                <ExternalLink className="h-4 w-4" aria-hidden="true" /> Abrir en pestaña nueva
              </Button>
              <Button variant="outline" size="sm" onClick={() => void descargar()} disabled={descargando}>
                {descargando ? <Spinner /> : <Download className="h-4 w-4" aria-hidden="true" />}
                {descargando ? 'Descargando…' : 'Descargar'}
              </Button>
            </div>
          </div>

          <div className="relative min-h-0 flex-1 bg-secondary/40">
            {estado === 'cargando' ? (
              <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground" role="status">
                <Spinner /> Cargando documento…
              </div>
            ) : null}

            {estado === 'error' ? (
              <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
                <p className="text-sm text-destructive-text" role="alert">No pudimos abrir el documento.</p>
                <Button variant="outline" size="sm" onClick={() => setIntento((valor) => valor + 1)}>
                  Reintentar
                </Button>
              </div>
            ) : null}

            {estado === 'listo' && url ? (
              <iframe
                title={`${documento.numero} · ${documento.tipo}`}
                src={url}
                className="h-full w-full border-0 bg-white"
              />
            ) : null}
          </div>
        </DialogContent>
      ) : null}
    </Dialog>
  )
}
