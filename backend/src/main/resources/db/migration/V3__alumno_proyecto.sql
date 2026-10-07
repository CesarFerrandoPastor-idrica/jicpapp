-- Participacion de alumnos en proyectos (M:N con rol).
-- El creador deja de ser implicito: es la fila con id_rol = 1.

CREATE TABLE rol_proyecto (
    id_rol SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT
);

-- Los ids se fijan a mano porque el indice parcial de mas abajo necesita un literal
-- (PostgreSQL exige que el predicado de un indice sea inmutable, no admite subconsultas).
INSERT INTO rol_proyecto (id_rol, nombre, descripcion) VALUES
    (1, 'Creador',     'Fundó el proyecto. Solo puede haber uno por proyecto'),
    (2, 'Socio',       'Participa en el desarrollo y comparte la responsabilidad del proyecto'),
    (3, 'Colaborador', 'Apoya el proyecto de forma puntual');

SELECT setval(pg_get_serial_sequence('rol_proyecto', 'id_rol'), (SELECT MAX(id_rol) FROM rol_proyecto));

CREATE TABLE alumno_proyecto (

    id_alumno INT NOT NULL,

    id_proyecto INT NOT NULL,

    id_rol INT NOT NULL,

    PRIMARY KEY(id_alumno,id_proyecto),

    CONSTRAINT fk_ap_alumno
        FOREIGN KEY(id_alumno)
        REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_ap_proyecto
        FOREIGN KEY(id_proyecto)
        REFERENCES proyecto(id_proyecto)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_ap_rol
        FOREIGN KEY(id_rol)
        REFERENCES rol_proyecto(id_rol)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- Un proyecto tiene como maximo un creador. La clave primaria (id_alumno, id_proyecto)
-- no lo impide por si sola: sin este indice, dos alumnos podrian figurar como creadores.
CREATE UNIQUE INDEX ux_un_creador_por_proyecto
    ON alumno_proyecto (id_proyecto)
    WHERE id_rol = 1;

-- La PK va encabezada por id_alumno, asi que las consultas "miembros de un proyecto"
-- necesitan su propio indice.
CREATE INDEX ix_ap_proyecto ON alumno_proyecto (id_proyecto);
CREATE INDEX ix_ap_rol ON alumno_proyecto (id_rol);
