import { useEffect, useState } from 'react'
import { Download, ExternalLink } from 'lucide-react'
import { contenidoDocumento, descargarDocumento } from '../lib/api'
import { Button } from './ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './ui/dialog'

/** Documento que el panel puede mostrar en línea, sin descargarlo. */
export type DocumentoVista = {
  id: number
  numero: string
  formato: 'PDF' | 'XML'
  nombreArchivo: string
  tipo: string
}

type Estado = 'cargando' | 'listo' | 'error'

/**
 * Diálogo de vista previa: trae los bytes con el token en la cabecera, crea un Blob URL y lo revoca
 * al cerrar. El PDF va en un iframe; el XML se muestra indentado y escapado por el navegador.
 */
export function DocumentoViewer({ documento, onCerrar }: {
  documento: DocumentoVista | null
  onCerrar: () => void
}) {
  const [estado, setEstado] = useState<Estado>('cargando')
  const [url, setUrl] = useState<string | null>(null)
  const [textoXml, setTextoXml] = useState<string | null>(null)
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    if (!documento) {
      setEstado('cargando')
      setUrl(null)
      setTextoXml(null)
      return
    }
    let vigente = true
    let creada: string | null = null
    setEstado('cargando')
    setUrl(null)
    setTextoXml(null)

    contenidoDocumento(documento)
      .then(async (blob) => {
        const objectUrl = URL.createObjectURL(blob)
        creada = objectUrl
        const xml = documento.formato === 'XML' ? formatearXml(await blob.text()) : null
        if (!vigente) return
        setUrl(objectUrl)
        setTextoXml(xml)
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

  const descargar = () => {
    if (!documento) return
    void descargarDocumento({ id: documento.id, nombreArchivo: documento.nombreArchivo })
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
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" size="sm" onClick={abrirEnPestana} disabled={estado !== 'listo' || !url}>
                <ExternalLink className="h-4 w-4" /> Abrir en pestaña nueva
              </Button>
              <Button variant="outline" size="sm" onClick={descargar}>
                <Download className="h-4 w-4" /> Descargar
              </Button>
            </div>
          </div>

          <div className="relative min-h-0 flex-1 bg-secondary/40">
            {estado === 'cargando' ? (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground" role="status">
                Cargando documento…
              </div>
            ) : null}

            {estado === 'error' ? (
              <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
                <p className="text-sm text-destructive" role="alert">No pudimos abrir el documento.</p>
                <Button variant="outline" size="sm" onClick={() => setIntento((valor) => valor + 1)}>
                  Reintentar
                </Button>
              </div>
            ) : null}

            {estado === 'listo' && documento.formato === 'PDF' && url ? (
              <iframe
                title={`${documento.numero} · ${documento.tipo}`}
                src={url}
                className="h-full w-full border-0 bg-white"
              />
            ) : null}

            {estado === 'listo' && documento.formato === 'XML' ? (
              <pre className="h-full overflow-auto p-6 text-xs leading-relaxed">
                <code className="font-mono whitespace-pre">{textoXml ?? ''}</code>
              </pre>
            ) : null}
          </div>
        </DialogContent>
      ) : null}
    </Dialog>
  )
}

/** Indenta el XML a dos espacios sin alterar el contenido ni los atributos. */
function formatearXml(texto: string): string {
  const documento = new DOMParser().parseFromString(texto, 'application/xml')
  if (documento.getElementsByTagName('parsererror').length > 0 || !documento.documentElement) {
    return texto
  }
  const lineas: string[] = []
  if (texto.trimStart().startsWith('<?xml')) {
    lineas.push('<?xml version="1.0" encoding="UTF-8"?>')
  }
  const serializador = new XMLSerializer()
  const recorrer = (nodo: Node, nivel: number) => {
    const sangria = '  '.repeat(nivel)
    if (nodo.nodeType === Node.ELEMENT_NODE) {
      const elemento = nodo as Element
      const hijos = Array.from(elemento.childNodes)
      const vacio = serializador.serializeToString(elemento.cloneNode(false))
      if (hijos.length === 0) {
        lineas.push(`${sangria}${vacio}`)
        return
      }
      lineas.push(`${sangria}${vacio.replace(/\/>$/, '>')}`)
      hijos.forEach((hijo) => recorrer(hijo, nivel + 1))
      lineas.push(`${sangria}</${elemento.tagName}>`)
    } else if (nodo.nodeType === Node.TEXT_NODE || nodo.nodeType === Node.CDATA_SECTION_NODE) {
      const contenido = serializador.serializeToString(nodo).trim()
      if (contenido) lineas.push(`${sangria}${contenido}`)
    }
  }
  recorrer(documento.documentElement, 0)
  return lineas.join('\n')
}
