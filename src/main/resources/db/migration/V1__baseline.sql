-- Esquema base de UPMarket. Generado a partir del esquema que creaba Hibernate
-- (MySQL 8, utf8mb4). Se usa CREATE TABLE IF NOT EXISTS para que la migración
-- también sirva como línea base en instalaciones que ya tienen las tablas.
-- Flyway está configurado con baseline-on-migrate=true y baseline-version=1:
-- una base existente sin historial se marca como V1 aplicada y no se toca.

CREATE TABLE IF NOT EXISTS categoria (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    nombre            VARCHAR(40)  NOT NULL,
    nombre_normalizado VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categoria_nombre (nombre_normalizado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS perfil (
    usuario   VARCHAR(120) NOT NULL,
    eliminada BIT(1)       NOT NULL,
    foto_url  VARCHAR(200) DEFAULT NULL,
    nombre    VARCHAR(60)  NOT NULL,
    telefono  VARCHAR(20)  DEFAULT NULL,
    PRIMARY KEY (usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS publicacion (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    categoria       VARCHAR(40)    NOT NULL,
    correo          VARCHAR(120)   DEFAULT NULL,
    descripcion     VARCHAR(2000)  NOT NULL,
    estado          ENUM ('DISPONIBLE', 'VENDIDA')   NOT NULL,
    estado_articulo ENUM ('NUEVO', 'USADO')          NOT NULL,
    precio          DECIMAL(11, 2) NOT NULL,
    publicada_en    DATETIME(6)    NOT NULL,
    texto_busqueda  VARCHAR(2200)  NOT NULL,
    titulo          VARCHAR(100)   NOT NULL,
    vendedor        VARCHAR(80)    NOT NULL,
    vendida_en      DATETIME(6)    DEFAULT NULL,
    version         BIGINT         DEFAULT NULL,
    whatsapp        VARCHAR(30)    DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS publicacion_foto (
    publicacion_id BIGINT       NOT NULL,
    tamano_bytes   BIGINT       NOT NULL,
    tipo           VARCHAR(30)  NOT NULL,
    url            VARCHAR(500) NOT NULL,
    orden          INT          NOT NULL,
    PRIMARY KEY (publicacion_id, orden),
    CONSTRAINT fk_publicacion_foto_publicacion
        FOREIGN KEY (publicacion_id) REFERENCES publicacion (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
