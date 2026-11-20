package com.upmarket.catalogo.domain.exception;

/** No existe un reporte de estadísticas con ese número. */
public class ReporteNoEncontradoException extends NoEncontradoException {

    public ReporteNoEncontradoException(String numero) {
        super("No existe el reporte " + numero);
    }
}
