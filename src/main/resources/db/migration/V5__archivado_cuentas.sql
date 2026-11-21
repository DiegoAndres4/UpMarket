-- Eliminar una cuenta ya no pierde el registro: el perfil se copia a perfiles_eliminados y sus
-- publicaciones se archivan (columna archivada_en) en lugar de borrarse. Así se libera el usuario
-- para una cuenta nueva y se conservan pagos, recibos y estadísticas.
CREATE TABLE IF NOT EXISTS perfiles_eliminados (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    usuario      VARCHAR(120) NOT NULL,
    nombre       VARCHAR(60)  NOT NULL,
    telefono     VARCHAR(20)  DEFAULT NULL,
    correo       VARCHAR(120) DEFAULT NULL,
    foto_url     VARCHAR(200) DEFAULT NULL,
    eliminado_en DATETIME(6)  NOT NULL,
    motivo       VARCHAR(30)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_perfiles_eliminados_usuario ON perfiles_eliminados (usuario, eliminado_en);

ALTER TABLE publicacion ADD COLUMN archivada_en DATETIME(6) DEFAULT NULL;
