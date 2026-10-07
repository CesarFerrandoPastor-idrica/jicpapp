-- Esquema inicial de JICP.
-- Orden obligatorio: las tablas referenciadas se crean antes que las que las apuntan.

CREATE TABLE colegio (
    id_colegio SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categoria_proyecto (
    id_categoria SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion TEXT
);

CREATE TABLE estado_proyecto (
    id_estado SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT
);

CREATE TABLE alumno (
    id_alumno SERIAL PRIMARY KEY,

    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(150) NOT NULL,

    email VARCHAR(255) NOT NULL UNIQUE,

    password VARCHAR(255) NOT NULL,

    id_colegio INT NOT NULL,

    jicp_inicial NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alumno_colegio
        FOREIGN KEY(id_colegio)
        REFERENCES colegio(id_colegio)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE TABLE proyecto (

    id_proyecto SERIAL PRIMARY KEY,

    nombre VARCHAR(150) NOT NULL,

    descripcion TEXT NOT NULL,

    id_categoria INT NOT NULL,

    id_estado INT NOT NULL,

    inversion_inicial NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    id_colegio INT NOT NULL,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categoria
        FOREIGN KEY(id_categoria)
        REFERENCES categoria_proyecto(id_categoria)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_estado
        FOREIGN KEY(id_estado)
        REFERENCES estado_proyecto(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_colegio
        FOREIGN KEY(id_colegio)
        REFERENCES colegio(id_colegio)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- Indices sobre las claves foraneas que se filtran en el listado del mercado.
CREATE INDEX ix_alumno_colegio ON alumno (id_colegio);
CREATE INDEX ix_proyecto_colegio ON proyecto (id_colegio);
CREATE INDEX ix_proyecto_categoria ON proyecto (id_categoria);
CREATE INDEX ix_proyecto_estado ON proyecto (id_estado);
