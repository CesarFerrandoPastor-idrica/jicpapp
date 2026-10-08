-- Cursos del profesorado y su asignacion al alumnado.
--
-- Un curso lo crea un profesor y lo asigna a alumnos de su mismo centro. El centro
-- del curso no se guarda aparte: es el de su profesor, y duplicarlo solo abriria la
-- puerta a que los dos dijeran cosas distintas.

CREATE TABLE curso (

    id_curso SERIAL PRIMARY KEY,

    titulo VARCHAR(150) NOT NULL,

    descripcion TEXT NOT NULL,

    -- Enlace opcional a un video, una presentacion o un documento del curso.
    url_recurso VARCHAR(500),

    -- Quien lo imparte. De el sale el centro, y con el los alumnos que puede asignar.
    id_profesor INT NOT NULL,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT titulo_no_vacio CHECK (length(trim(titulo)) > 0),

    CONSTRAINT fk_curso_profesor
        FOREIGN KEY(id_profesor)
        REFERENCES profesor(id_profesor)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- El listado habitual del profesor: "mis cursos".
CREATE INDEX ix_curso_profesor ON curso (id_profesor);

-- Que alumno tiene asignado que curso, y como va.
CREATE TABLE matricula (

    id_curso INT NOT NULL,

    id_alumno INT NOT NULL,

    -- Porcentaje completado, de 0 a 100. Nace a cero; lo movera el alumno al avanzar.
    progreso NUMERIC(5,2) NOT NULL DEFAULT 0,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Un alumno no puede estar dos veces en el mismo curso.
    PRIMARY KEY (id_curso, id_alumno),

    CONSTRAINT progreso_en_rango CHECK (progreso >= 0 AND progreso <= 100),

    -- Una matricula no tiene sentido sin su curso ni sin su alumno.
    CONSTRAINT fk_matricula_curso
        FOREIGN KEY(id_curso)
        REFERENCES curso(id_curso)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_matricula_alumno
        FOREIGN KEY(id_alumno)
        REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

-- El listado habitual del alumno: "mis cursos".
CREATE INDEX ix_matricula_alumno ON matricula (id_alumno);
