-- Monetización: las publicaciones nuevas nacen pendientes de pago y las existentes quedan aprobadas
-- (no se cobra retroactivamente). El contador de vistas se incrementa de forma atómica.
ALTER TABLE publicacion ADD COLUMN estado_pago VARCHAR(20) NOT NULL DEFAULT 'APROBADO';
ALTER TABLE publicacion ADD COLUMN vistas BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pago (
    id BIGINT NOT NULL AUTO_INCREMENT,
    publicacion_id BIGINT NOT NULL,
    monto DECIMAL(11, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    referencia VARCHAR(80) NOT NULL,
    creado_en DATETIME(6) NOT NULL,
    aprobado_en DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_pago_referencia UNIQUE (referencia),
    CONSTRAINT fk_pago_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion (id) ON DELETE CASCADE
);
