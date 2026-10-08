// Pruebas de los comentarios de un proyecto, sin backend.
//
// La pantalla recibe por Riverpod un repositorio falso y una sesión ya abierta,
// así se puede comprobar qué pinta y qué envía. Quién puede comentar y en qué
// centro lo decide el servidor (backend/src/test/kotlin/com/jicp/api/MercadoTest.kt).

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jicpapp/api/modelos_auth.dart';
import 'package:jicpapp/api/modelos_proyecto.dart';
import 'package:jicpapp/api/proveedores.dart';
import 'package:jicpapp/api/repositorio_proyectos.dart';
import 'package:jicpapp/api/sesion.dart';
import 'package:jicpapp/screens/comentarios_screen.dart';

const _miId = 7;

Comentario _comentario(int id, {int idAlumno = 99, String texto = 'Buen proyecto'}) =>
    Comentario(
      id: id,
      idAlumno: idAlumno,
      autor: 'Alumno $idAlumno',
      texto: texto,
      fecha: DateTime.now().subtract(const Duration(minutes: 5)),
    );

class _RepositorioFalso implements RepositorioProyectos {
  /// Todo el hilo, del más reciente al más antiguo. Se sirve por páginas.
  final hilo = <Comentario>[];

  /// Si no es null, `comentar` lanza este error en vez de publicar.
  ErrorApi? fallarAlComentar;

  final paginasPedidas = <int>[];
  final textosEnviados = <String>[];

  @override
  Future<PaginaDeComentarios> comentarios(
    int idProyecto, {
    int pagina = 0,
    int tamano = 20,
  }) async {
    paginasPedidas.add(pagina);
    final desde = (pagina * tamano).clamp(0, hilo.length);
    final hasta = (desde + tamano).clamp(0, hilo.length);
    return PaginaDeComentarios(
      comentarios: hilo.sublist(desde, hasta),
      total: hilo.length,
      esLaUltima: hasta >= hilo.length,
    );
  }

  @override
  Future<Comentario> comentar(int idProyecto, String texto) async {
    textosEnviados.add(texto);
    if (fallarAlComentar != null) throw fallarAlComentar!;
    // Como el servidor: devuelve el comentario guardado, con su id y su autor.
    final nuevo = _comentario(1000 + textosEnviados.length,
        idAlumno: _miId, texto: texto.trim());
    hilo.insert(0, nuevo);
    return nuevo;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Una sesión ya abierta, sin pasar por el login.
class _SesionAbierta extends Sesion {
  _SesionAbierta(this.rol);

  final Rol rol;

  @override
  EstadoDeSesion build() => EstadoDeSesion(
        perfil: Perfil(
          idUsuario: 1,
          email: 'yo@ies.example',
          rol: rol,
          alumno: rol == Rol.alumno
              ? const PerfilAlumno(
                  id: _miId,
                  nombre: 'Yo',
                  apellido: 'Mismo',
                  idColegio: 1,
                  nombreColegio: 'IES',
                  jicpInicial: 1000,
                )
              : null,
        ),
      );
}

Future<void> _montar(
  WidgetTester tester,
  _RepositorioFalso repo, {
  Rol rol = Rol.alumno,
}) async {
  await tester.pumpWidget(ProviderScope(
    overrides: [
      repositorioProyectosProvider.overrideWithValue(repo),
      sesionProvider.overrideWith(() => _SesionAbierta(rol)),
    ],
    child: const MaterialApp(
      home: ComentariosScreen(idProyecto: 3, nombreProyecto: 'Huerto'),
    ),
  ));
  await tester.pumpAndSettle();
}

IconButton _botonPublicar(WidgetTester tester) =>
    tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.send));

void main() {
  group('haceCuanto', () {
    final ahora = DateTime(2026, 10, 7, 12, 0);

    test('formatea la antigüedad', () {
      expect(haceCuanto(ahora.subtract(const Duration(seconds: 20)), ahora), 'Ahora');
      expect(haceCuanto(ahora.subtract(const Duration(minutes: 5)), ahora), 'hace 5 min');
      expect(haceCuanto(ahora.subtract(const Duration(hours: 3)), ahora), 'hace 3 h');
      expect(haceCuanto(ahora.subtract(const Duration(days: 1)), ahora), 'hace 1 día');
      expect(haceCuanto(ahora.subtract(const Duration(days: 3)), ahora), 'hace 3 días');
      expect(haceCuanto(DateTime(2026, 9, 1, 10), ahora), '01/09/2026');
    });

    test('un reloj del móvil atrasado no da tiempos negativos', () {
      expect(haceCuanto(ahora.add(const Duration(minutes: 2)), ahora), 'Ahora');
    });
  });

  test('Comentario lee la respuesta de la API', () {
    final c = Comentario.desdeJson({
      'id': 5,
      'idProyecto': 3,
      'idAlumno': 7,
      'nombre': 'Ana',
      'apellido': 'Martinez',
      'texto': 'Me encanta',
      'fecha': '2026-10-07T10:29:08.91292',
    });
    expect(c.autor, 'Ana Martinez');
    expect(c.idAlumno, 7);
    expect(c.fecha, DateTime(2026, 10, 7, 10, 29, 8, 912, 920));
  });

  testWidgets('un hilo vacío invita a comentar', (tester) async {
    await _montar(tester, _RepositorioFalso());
    expect(find.textContaining('Todavía no hay comentarios'), findsOneWidget);
  });

  testWidgets('marca como "Tú" los comentarios propios', (tester) async {
    final repo = _RepositorioFalso()
      ..hilo.addAll([
        _comentario(2, idAlumno: _miId, texto: 'El mío'),
        _comentario(1, texto: 'El de otro'),
      ]);
    await _montar(tester, repo);

    expect(find.text('Tú'), findsOneWidget);
    expect(find.text('Alumno 99'), findsOneWidget);
  });

  testWidgets('publicar pinta arriba lo que devuelve el servidor y vacía la caja',
      (tester) async {
    final repo = _RepositorioFalso()..hilo.add(_comentario(1, texto: 'Anterior'));
    await _montar(tester, repo);

    expect(_botonPublicar(tester).onPressed, isNull, reason: 'caja vacía');
    await tester.enterText(find.byType(TextField), '  Gran idea  ');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.send));
    await tester.pumpAndSettle();

    expect(repo.textosEnviados, ['  Gran idea  ']);
    expect(find.text('Gran idea'), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, isEmpty);
    // El nuevo queda encima del anterior.
    expect(
      tester.getTopLeft(find.text('Gran idea')).dy,
      lessThan(tester.getTopLeft(find.text('Anterior')).dy),
    );
  });

  testWidgets('si el servidor lo rechaza se avisa y no se pierde el texto',
      (tester) async {
    final repo = _RepositorioFalso()
      ..fallarAlComentar =
          const ErrorApi('Solo puedes comentar proyectos de tu propio centro', codigo: 403);
    await _montar(tester, repo);

    await tester.enterText(find.byType(TextField), 'Hola');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.send));
    await tester.pumpAndSettle();

    expect(find.text('Solo puedes comentar proyectos de tu propio centro'), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, 'Hola');
  });

  testWidgets('solo con espacios no se puede publicar', (tester) async {
    await _montar(tester, _RepositorioFalso());
    await tester.enterText(find.byType(TextField), '    ');
    await tester.pump();
    expect(_botonPublicar(tester).onPressed, isNull);
  });

  testWidgets('un profesor lee el hilo pero no ve la caja de texto', (tester) async {
    final repo = _RepositorioFalso()..hilo.add(_comentario(1));
    await _montar(tester, repo, rol: Rol.profesor);

    expect(find.text('Buen proyecto'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
  });

  testWidgets('al llegar al final pide las páginas siguientes hasta el último',
      (tester) async {
    final repo = _RepositorioFalso()
      ..hilo.addAll([for (var i = 45; i >= 1; i--) _comentario(i, texto: 'Comentario $i')]);
    await _montar(tester, repo);

    expect(repo.paginasPedidas, [0]);
    await tester.dragUntilVisible(
      find.text('Comentario 1'),
      find.byType(ListView),
      const Offset(0, -500),
    );
    await tester.pumpAndSettle();

    expect(repo.paginasPedidas, [0, 1, 2]);
    expect(find.text('Comentario 1'), findsOneWidget);
  });

  testWidgets('si alguien comenta mientras se lee, la página siguiente no repite',
      (tester) async {
    final repo = _RepositorioFalso()
      ..hilo.addAll([for (var i = 45; i >= 1; i--) _comentario(i, texto: 'Comentario $i')]);
    await _montar(tester, repo);

    // Ya se ha leído la página 0 (del 45 al 26). Otro alumno comenta: todo el hilo
    // se desplaza una posición y la página 1 empieza otra vez por el 26.
    repo.hilo.insert(0, _comentario(46, texto: 'Comentario 46'));

    await tester.dragUntilVisible(
      find.text('Comentario 25'),
      find.byType(ListView),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    expect(repo.paginasPedidas, containsAllInOrder([0, 1]));
    // El 26 queda justo encima del 25: si se hubiera repetido, saldría dos veces.
    expect(find.text('Comentario 26', skipOffstage: false), findsOneWidget);
  });
}
