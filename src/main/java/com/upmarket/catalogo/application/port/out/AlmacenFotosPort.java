package com.upmarket.catalogo.application.port.out;

import com.upmarket.catalogo.domain.model.Foto;

/**
 * Puerto de salida para guardar y borrar fotos. El adaptador decide DÓNDE se guardan
 * (disco local o Cloudinary) sin que la aplicación lo sepa. Lo que se guarda en el
 * dominio es una {@link Foto}: su URL, su tipo y su tamaño.
 */
public interface AlmacenFotosPort {

    /** Carpeta remota de las fotos de publicaciones. */
    String CARPETA_PUBLICACIONES = "upmarket/publicaciones";

    /** Carpeta remota de las fotos de perfil. */
    String CARPETA_PERFILES = "upmarket/perfiles";

    /**
     * Guarda una imagen validando su CONTENIDO (JPG o PNG, hasta 5 MB) y devuelve la foto
     * con la URL con la que se sirve o se referencia.
     *
     * @param contenido bytes de la imagen
     * @param carpeta   carpeta lógica del proveedor remoto (se ignora en el almacenamiento local)
     */
    Foto guardar(byte[] contenido, String carpeta);

    /**
     * Borra una foto a partir de la URL o el nombre con el que se guardó.
     * Si no existe o no la puede borrar, no falla: borrar una foto nunca debe impedir
     * cambiar la foto de perfil ni eliminar una cuenta.
     */
    void borrar(String url);
}
