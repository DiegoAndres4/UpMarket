-- Documentos generados por el panel de administración: recibos de pago (PDF) y reportes de
-- estadísticas (PDF/XML). Se guardan PRIVADOS: la referencia es el public_id del almacén, nunca
-- una URL pública. La clave única impide dos recibos para el mismo pago; los reportes llevan
-- pago_id nulo y comparten el número EST- entre sus dos formatos.
CREATE TABLE IF NOT EXISTS documento_generado (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tipo           VARCHAR(40)  NOT NULL,
    formato        VARCHAR(10)  NOT NULL,
    numero         VARCHAR(20)  DEFAULT NULL,
    pago_id        BIGINT       DEFAULT NULL,
    proveedor      VARCHAR(30)  DEFAULT NULL,
    referencia     VARCHAR(255) DEFAULT NULL,
    nombre_archivo VARCHAR(255) DEFAULT NULL,
    tamano_bytes   BIGINT       DEFAULT NULL,
    generado_en    DATETIME(6)  NOT NULL,
    generado_por   VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_documento_recibo UNIQUE (tipo, formato, pago_id),
    CONSTRAINT fk_documento_pago FOREIGN KEY (pago_id) REFERENCES pago (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
