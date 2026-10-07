-- Libro contable de JICP, mercado de participaciones y comentarios.
--
-- El saldo deja de ser un numero suelto y pasa a ser el resultado de una secuencia
-- de apuntes inmutables. Toda operacion escribe dos o mas apuntes que suman cero:
-- asi, "SUM(importe) <> 0" detecta dinero creado de la nada con una sola consulta.

-- ---------------------------------------------------------------------------
-- 1. Cuentas
-- ---------------------------------------------------------------------------

CREATE TABLE cuenta (
    id_cuenta SERIAL PRIMARY KEY,

    tipo VARCHAR(20) NOT NULL
        CHECK (tipo IN ('CARTERA_ALUMNO', 'TESORERIA_PROYECTO', 'EMISION_SISTEMA')),

    id_alumno INT,

    id_proyecto INT,

    -- Saldo cacheado. Es redundante con la suma de los apuntes a proposito: no se
    -- puede poner un CHECK sobre un SUM(), y esta restriccion es la ultima linea de
    -- defensa contra un descubierto que se le escape a la capa de servicio.
    saldo NUMERIC(12,2) NOT NULL DEFAULT 0.00,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Nadie puede quedarse en negativo salvo la cuenta de emision, cuyo saldo
    -- negativo ES la masa monetaria en circulacion.
    CONSTRAINT saldo_no_negativo
        CHECK (tipo = 'EMISION_SISTEMA' OR saldo >= 0),

    -- Cada tipo de cuenta tiene exactamente un dueno, y del tipo que le toca.
    CONSTRAINT dueno_coherente CHECK (
        (tipo = 'CARTERA_ALUMNO'     AND id_alumno IS NOT NULL AND id_proyecto IS NULL) OR
        (tipo = 'TESORERIA_PROYECTO' AND id_proyecto IS NOT NULL AND id_alumno IS NULL) OR
        (tipo = 'EMISION_SISTEMA'    AND id_alumno IS NULL AND id_proyecto IS NULL)
    ),

    CONSTRAINT fk_cuenta_alumno
        FOREIGN KEY(id_alumno) REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT fk_cuenta_proyecto
        FOREIGN KEY(id_proyecto) REFERENCES proyecto(id_proyecto)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Una cartera por alumno y una tesoreria por proyecto. Indices parciales porque
-- la columna es NULL para los otros tipos.
CREATE UNIQUE INDEX ux_cuenta_por_alumno   ON cuenta (id_alumno)   WHERE tipo = 'CARTERA_ALUMNO';
CREATE UNIQUE INDEX ux_cuenta_por_proyecto ON cuenta (id_proyecto) WHERE tipo = 'TESORERIA_PROYECTO';
-- Y una sola cuenta de emision en todo el sistema.
CREATE UNIQUE INDEX ux_cuenta_emision ON cuenta ((1)) WHERE tipo = 'EMISION_SISTEMA';


-- ---------------------------------------------------------------------------
-- 2. Operaciones y apuntes
-- ---------------------------------------------------------------------------

CREATE TABLE operacion_jicp (
    id_operacion SERIAL PRIMARY KEY,

    tipo VARCHAR(30) NOT NULL
        CHECK (tipo IN ('CONCESION_INICIAL', 'CREACION_PROYECTO', 'INVERSION',
                        'DESINVERSION', 'RENDIMIENTO', 'AJUSTE_ADMIN', 'MIGRACION')),

    id_alumno_actor INT NOT NULL,

    id_proyecto INT,

    -- La garantia real contra duplicados. Un "if (ya existe) return" previo tiene
    -- condicion de carrera entre la consulta y el INSERT; este indice unico no.
    clave_idempotencia VARCHAR(64) NOT NULL,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_idempotencia UNIQUE (id_alumno_actor, clave_idempotencia),

    CONSTRAINT fk_operacion_actor
        FOREIGN KEY(id_alumno_actor) REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT fk_operacion_proyecto
        FOREIGN KEY(id_proyecto) REFERENCES proyecto(id_proyecto)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE apunte_jicp (
    id_apunte BIGSERIAL PRIMARY KEY,

    id_operacion INT NOT NULL,

    id_cuenta INT NOT NULL,

    -- Negativo = sale de la cuenta, positivo = entra. Un apunte de cero no dice nada.
    importe NUMERIC(12,2) NOT NULL CHECK (importe <> 0),

    -- Saldo de la cuenta despues de este apunte. Da auditoria instantanea
    -- ("por que tengo este saldo") y es fiable porque el bloqueo de fila serializa
    -- los apuntes de una misma cuenta.
    saldo_posterior NUMERIC(12,2) NOT NULL,

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_apunte_operacion
        FOREIGN KEY(id_operacion) REFERENCES operacion_jicp(id_operacion)
        ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT fk_apunte_cuenta
        FOREIGN KEY(id_cuenta) REFERENCES cuenta(id_cuenta)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- El extracto de una cuenta se lee del mas reciente al mas antiguo.
CREATE INDEX ix_apunte_por_cuenta ON apunte_jicp (id_cuenta, id_apunte DESC);
CREATE INDEX ix_apunte_por_operacion ON apunte_jicp (id_operacion);


-- ---------------------------------------------------------------------------
-- 3. El proyecto pasa a ser emisor de participaciones
-- ---------------------------------------------------------------------------

ALTER TABLE proyecto
    -- Precio de la primera participacion. El precio real lo calcula el servidor a
    -- partir de este y de cuantas van emitidas; el cliente nunca lo envia.
    ADD COLUMN precio_base NUMERIC(12,2) NOT NULL DEFAULT 100.00,

    -- Tamano de la ronda: sirve de referencia para la subida de precio y de tope.
    ADD COLUMN participaciones_totales NUMERIC(14,4) NOT NULL DEFAULT 100.0000,

    ADD COLUMN participaciones_emitidas NUMERIC(14,4) NOT NULL DEFAULT 0.0000;

ALTER TABLE proyecto
    ADD CONSTRAINT precio_base_positivo CHECK (precio_base > 0),
    ADD CONSTRAINT ronda_positiva CHECK (participaciones_totales > 0),
    -- No se puede emitir mas capital del que la ronda contempla.
    ADD CONSTRAINT emision_dentro_de_la_ronda
        CHECK (participaciones_emitidas >= 0 AND participaciones_emitidas <= participaciones_totales);


-- ---------------------------------------------------------------------------
-- 4. Posiciones y movimientos del mercado
-- ---------------------------------------------------------------------------

CREATE TABLE inversion (
    id_inversion SERIAL PRIMARY KEY,

    id_proyecto INT NOT NULL,

    id_alumno INT NOT NULL,

    participaciones NUMERIC(14,4) NOT NULL CHECK (participaciones >= 0),

    -- Precio medio de adquisicion. Con precio variable es lo unico que permite
    -- saber si la posicion gana o pierde respecto al precio actual.
    precio_medio NUMERIC(12,4) NOT NULL CHECK (precio_medio >= 0),

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Un alumno tiene una sola posicion por proyecto: las compras sucesivas
    -- acumulan participaciones y recalculan el precio medio.
    CONSTRAINT ux_inversion_por_alumno_y_proyecto UNIQUE (id_proyecto, id_alumno),

    CONSTRAINT fk_inversion_proyecto
        FOREIGN KEY(id_proyecto) REFERENCES proyecto(id_proyecto)
        ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT fk_inversion_alumno
        FOREIGN KEY(id_alumno) REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX ix_inversion_alumno ON inversion (id_alumno);

CREATE TABLE movimiento_inversion (
    id_movimiento BIGSERIAL PRIMARY KEY,

    id_inversion INT NOT NULL,

    -- La operacion contable que movio el dinero de este movimiento. Enlaza el
    -- detalle bursatil con el libro: cada compra tiene su contrapartida en apuntes.
    id_operacion INT NOT NULL,

    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('COMPRA', 'VENTA')),

    participaciones NUMERIC(14,4) NOT NULL CHECK (participaciones > 0),

    precio_unitario NUMERIC(12,4) NOT NULL CHECK (precio_unitario > 0),

    importe NUMERIC(12,2) NOT NULL CHECK (importe > 0),

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_movimiento_inversion
        FOREIGN KEY(id_inversion) REFERENCES inversion(id_inversion)
        ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT fk_movimiento_operacion
        FOREIGN KEY(id_operacion) REFERENCES operacion_jicp(id_operacion)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX ix_movimiento_por_inversion ON movimiento_inversion (id_inversion, id_movimiento DESC);


-- ---------------------------------------------------------------------------
-- 5. Comentarios
-- ---------------------------------------------------------------------------

CREATE TABLE comentario_proyecto (
    id_comentario SERIAL PRIMARY KEY,

    id_proyecto INT NOT NULL,

    id_alumno INT NOT NULL,

    texto TEXT NOT NULL CHECK (LENGTH(TRIM(texto)) > 0),

    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comentario_proyecto
        FOREIGN KEY(id_proyecto) REFERENCES proyecto(id_proyecto)
        ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT fk_comentario_alumno
        FOREIGN KEY(id_alumno) REFERENCES alumno(id_alumno)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- El hilo de un proyecto se lee del mas reciente al mas antiguo.
CREATE INDEX ix_comentario_por_proyecto ON comentario_proyecto (id_proyecto, id_comentario DESC);


-- ---------------------------------------------------------------------------
-- 6. Traer al libro lo que ya existia
-- ---------------------------------------------------------------------------
-- Alumnos y proyectos creados antes de que hubiera contabilidad. Su dinero entra
-- como emision, que es exactamente como entra el dinero en este sistema: contra la
-- cuenta EMISION_SISTEMA, cuyo saldo negativo es la masa monetaria. Asi el libro
-- cuadra a cero desde el primer dia en lugar de arrancar con dinero aparecido.

INSERT INTO cuenta (tipo, saldo) VALUES ('EMISION_SISTEMA', 0.00);

-- Una cartera por alumno, con su concesion inicial.
INSERT INTO cuenta (tipo, id_alumno, saldo)
SELECT 'CARTERA_ALUMNO', id_alumno, 0.00 FROM alumno;

-- Una tesoreria por proyecto.
INSERT INTO cuenta (tipo, id_proyecto, saldo)
SELECT 'TESORERIA_PROYECTO', id_proyecto, 0.00 FROM proyecto;

-- Concesion inicial de cada alumno que ya tenia jicp_inicial > 0.
INSERT INTO operacion_jicp (tipo, id_alumno_actor, clave_idempotencia)
SELECT 'CONCESION_INICIAL', id_alumno, 'migracion-v6-concesion-' || id_alumno
FROM alumno
WHERE jicp_inicial > 0;

-- Apunte que carga la cuenta de emision...
INSERT INTO apunte_jicp (id_operacion, id_cuenta, importe, saldo_posterior)
SELECT o.id_operacion,
       (SELECT id_cuenta FROM cuenta WHERE tipo = 'EMISION_SISTEMA'),
       -a.jicp_inicial,
       0.00
FROM operacion_jicp o
JOIN alumno a ON a.id_alumno = o.id_alumno_actor
WHERE o.tipo = 'CONCESION_INICIAL';

-- ...y el que abona la cartera del alumno.
INSERT INTO apunte_jicp (id_operacion, id_cuenta, importe, saldo_posterior)
SELECT o.id_operacion,
       c.id_cuenta,
       a.jicp_inicial,
       a.jicp_inicial
FROM operacion_jicp o
JOIN alumno a ON a.id_alumno = o.id_alumno_actor
JOIN cuenta c ON c.id_alumno = a.id_alumno AND c.tipo = 'CARTERA_ALUMNO'
WHERE o.tipo = 'CONCESION_INICIAL';

-- Saldos cacheados coherentes con los apuntes recien escritos.
UPDATE cuenta c
SET saldo = a.jicp_inicial
FROM alumno a
WHERE c.id_alumno = a.id_alumno AND c.tipo = 'CARTERA_ALUMNO';

UPDATE cuenta
SET saldo = -COALESCE((SELECT SUM(jicp_inicial) FROM alumno WHERE jicp_inicial > 0), 0)
WHERE tipo = 'EMISION_SISTEMA';

-- El saldo_posterior de los apuntes de emision se deja consistente con el recorrido
-- acumulado; al ser todos de la misma migracion, basta con el total final.
UPDATE apunte_jicp
SET saldo_posterior = (SELECT saldo FROM cuenta WHERE tipo = 'EMISION_SISTEMA')
WHERE id_cuenta = (SELECT id_cuenta FROM cuenta WHERE tipo = 'EMISION_SISTEMA');
