CREATE TABLE IF NOT EXISTS resena (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    vendedor   VARCHAR(120) NOT NULL,
    autor      VARCHAR(120) NOT NULL,
    estrellas  INT          NOT NULL,
    comentario VARCHAR(500) NOT NULL,
    creada_en  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resena_vendedor_autor (vendedor, autor),
    KEY idx_resena_vendedor (vendedor),
    CONSTRAINT ck_resena_estrellas CHECK (estrellas BETWEEN 0 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
