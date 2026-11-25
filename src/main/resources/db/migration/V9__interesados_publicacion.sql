-- Personas que pidieron el contacto de una publicación. Una fila por persona y publicación;
-- veces y ultima_vez se actualizan en cada nueva solicitud.
CREATE TABLE IF NOT EXISTS publicacion_interes (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    publicacion_id BIGINT       NOT NULL,
    usuario        VARCHAR(120) NOT NULL,
    veces          INT          NOT NULL DEFAULT 1,
    ultima_vez     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interes_publicacion_usuario (publicacion_id, usuario),
    CONSTRAINT fk_interes_publicacion FOREIGN KEY (publicacion_id)
        REFERENCES publicacion (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
