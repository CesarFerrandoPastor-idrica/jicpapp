-- Identidad unificada. Hasta ahora las credenciales colgaban de alumno; con el profesorado
-- a la vuelta de la esquina eso obligaria a buscar el login en dos tablas y permitiria que
-- un alumno y un profesor compartiesen email. Se extraen a "usuario", que pasa a ser el
-- unico sitio donde vive un email y una contrasena.

CREATE TABLE usuario (
    id_usuario SERIAL PRIMARY KEY,

    email VARCHAR(255) NOT NULL UNIQUE,

    -- Hash BCrypt. Nunca la contrasena en claro ni cifrada de forma reversible.
    password VARCHAR(255) NOT NULL,

    rol VARCHAR(20) NOT NULL
        CHECK (rol IN ('ALUMNO', 'PROFESOR', 'ADMIN')),

    -- Dar de baja a alguien no borra su rastro en proyectos ni (mas adelante) en el libro contable.
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cada alumno existente se lleva sus credenciales tal cual: el hash BCrypt se copia,
-- asi que las contrasenas ya registradas siguen siendo validas.
INSERT INTO usuario (email, password, rol, fecha_registro)
SELECT LOWER(TRIM(email)), password, 'ALUMNO', fecha_registro
FROM alumno;

ALTER TABLE alumno ADD COLUMN id_usuario INT;

UPDATE alumno a
SET id_usuario = u.id_usuario
FROM usuario u
WHERE u.email = LOWER(TRIM(a.email));

ALTER TABLE alumno ALTER COLUMN id_usuario SET NOT NULL;

-- Un usuario es como mucho un alumno. Sin esta unicidad, dos filas de alumno
-- podrian apuntar al mismo login.
ALTER TABLE alumno ADD CONSTRAINT ux_alumno_usuario UNIQUE (id_usuario);

ALTER TABLE alumno ADD CONSTRAINT fk_alumno_usuario
    FOREIGN KEY (id_usuario)
    REFERENCES usuario(id_usuario)
    ON UPDATE CASCADE
    ON DELETE RESTRICT;

-- Ya no hay dos fuentes de verdad para el email: se queda solo la de usuario.
ALTER TABLE alumno DROP COLUMN email;
ALTER TABLE alumno DROP COLUMN password;


-- Refresh tokens rotativos: cada uso quema el token y emite uno nuevo.
CREATE TABLE refresh_token (
    id_refresh_token BIGSERIAL PRIMARY KEY,

    id_usuario INT NOT NULL,

    -- SHA-256 en hexadecimal del token, nunca el token en claro: quien lea esta tabla
    -- no puede suplantar a nadie. No lleva BCrypt porque el valor ya son 256 bits
    -- aleatorios, no una contrasena adivinable a fuerza bruta.
    hash_token VARCHAR(64) NOT NULL UNIQUE,

    fecha_expiracion TIMESTAMP NOT NULL,

    -- NULL mientras el token sigue vivo. Se rellena al rotarlo o al cerrar sesion.
    fecha_revocacion TIMESTAMP,

    -- Token que lo sustituyo al rotar. Deja la cadena de la sesion reconstruible.
    id_sustituto BIGINT,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_sustituto
        FOREIGN KEY(id_sustituto)
        REFERENCES refresh_token(id_refresh_token)
        ON UPDATE CASCADE
        ON DELETE SET NULL
);

-- Revocar la sesion entera de un usuario es la operacion caliente: se hace en cuanto
-- se detecta la reutilizacion de un token ya rotado.
CREATE INDEX ix_refresh_token_usuario ON refresh_token (id_usuario)
    WHERE fecha_revocacion IS NULL;
