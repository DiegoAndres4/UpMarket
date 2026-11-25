CREATE TABLE foto_subida (
    id BIGINT NOT NULL AUTO_INCREMENT,
    url VARCHAR(500) NOT NULL,
    usuario_id BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_foto_subida_url (url)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;
