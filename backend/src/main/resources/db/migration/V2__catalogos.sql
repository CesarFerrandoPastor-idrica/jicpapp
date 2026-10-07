-- Catalogos base. Las categorias coinciden con los filtros de la pantalla
-- de mercado de la app Flutter (lib/screens/market_screen.dart).

INSERT INTO categoria_proyecto (nombre, descripcion) VALUES
    ('Tecnología', 'Software, hardware, IA y transformación digital'),
    ('Salud',      'Bienestar, biotecnología y servicios sanitarios'),
    ('Finanzas',   'Fintech, banca, seguros e inversión'),
    ('Educación',  'Formación, edtech y divulgación'),
    ('Media',      'Contenido, entretenimiento y comunicación');

INSERT INTO estado_proyecto (nombre, descripcion) VALUES
    ('Borrador',  'En preparación por el alumno, no visible en el mercado'),
    ('Publicado', 'Visible en el mercado y abierto a inversión'),
    ('Cerrado',   'Finalizado, ya no admite nuevas inversiones');
