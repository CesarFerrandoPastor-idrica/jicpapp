-- El profesorado tambien comenta los proyectos de su alumnado.
--
-- Hasta ahora el autor de un comentario era siempre un alumno. Ahora puede ser un
-- alumno o un profesor, y exactamente uno de los dos: un comentario sin autor, o con
-- dos, no tiene sentido. Lo garantiza un CHECK, no solo el codigo.

ALTER TABLE comentario_proyecto
    ALTER COLUMN id_alumno DROP NOT NULL;

ALTER TABLE comentario_proyecto
    ADD COLUMN id_profesor INT;

ALTER TABLE comentario_proyecto
    ADD CONSTRAINT fk_comentario_profesor
        FOREIGN KEY(id_profesor) REFERENCES profesor(id_profesor)
        ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE comentario_proyecto
    ADD CONSTRAINT un_solo_autor
        CHECK ((id_alumno IS NULL) <> (id_profesor IS NULL));
