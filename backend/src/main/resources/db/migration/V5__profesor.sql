-- El profesorado. Mismo patron que alumno: los datos de acceso ya viven en
-- usuario (V4), asi que aqui solo va lo que es propio de ser profesor.
--
-- De esta tabla dependen cursos, evaluacion y ranking: hasta ahora no habia
-- forma de decir "este curso lo imparte fulano" ni de limitar a un docente a su
-- propio centro.

CREATE TABLE profesor (

    id_profesor SERIAL PRIMARY KEY,

    -- Credenciales y rol. Un usuario es como mucho un profesor, igual que en alumno.
    id_usuario INT NOT NULL,

    nombre VARCHAR(100) NOT NULL,

    apellido VARCHAR(150) NOT NULL,

    -- El centro donde imparte. Es lo que acota su acceso: un profesor solo ve
    -- alumnos, cursos y notas de su propio colegio.
    id_colegio INT NOT NULL,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_profesor_usuario UNIQUE (id_usuario),

    CONSTRAINT fk_profesor_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES usuario(id_usuario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_profesor_colegio
        FOREIGN KEY(id_colegio)
        REFERENCES colegio(id_colegio)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- El filtro habitual sera "profesorado de este centro".
CREATE INDEX ix_profesor_colegio ON profesor (id_colegio);
