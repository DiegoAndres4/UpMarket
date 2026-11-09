-- Se ejecuta una sola vez, al crear el volumen de MySQL por primera vez.
-- Dos bases separadas: cada servicio es dueño de sus datos.
CREATE DATABASE IF NOT EXISTS authcoredb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS upmarket   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- El usuario (MYSQL_USER) lo crea la imagen; aquí solo se le dan permisos sobre ambas bases.
GRANT ALL PRIVILEGES ON authcoredb.* TO 'upmarket'@'%';
GRANT ALL PRIVILEGES ON upmarket.*   TO 'upmarket'@'%';
FLUSH PRIVILEGES;
