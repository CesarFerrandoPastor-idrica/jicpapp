// Pruebas de la parte del profesorado (y de los cursos del alumno), sin backend.
//
// Las pantallas reciben repositorios falsos y una sesión ya abierta por Riverpod.
// Qué alumnos ve cada profesor y a quién puede asignar un curso lo decide el
// servidor (backend/src/test/kotlin/com/jicp/api/AulaTest.kt).

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jicpapp/api/modelos_auth.dart';
import 'package:jicpapp/api/modelos_aula.dart';
import 'package:jicpapp/api/modelos_proyecto.dart';
import 'package:jicpapp/api/proveedores.dart';
import 'package:jicpapp/api/repositorio_aula.dart';
import 'package:jicpapp/api/repositorio_proyectos.dart';
import 'package:jicpapp/api/sesion.dart';
import 'package:jicpapp/screens/cursos_profesor_screen.dart';
import 'package:jicpapp/screens/editar_cuenta_screen.dart';
import 'package:jicpapp/screens/ficha_proyecto_screen.dart';
import 'package:jicpapp/screens/learning_screen.dart';
import 'package:jicpapp/screens/ranking_screen.dart';

class _AulaFalsa implements RepositorioAula {
  List<EntradaDeRanking> rankingDevuelto = const [];
  List<Curso> cursosDevueltos = const [];
  List<AlumnoDelCentro> alumnos = const [];

  final cursosCreados = <Map<String, Object?>>[];
  final cursosEditados = <Map<String, Object?>>[];

  @override
  Future<Curso> actualizarCurso({
    required int id,
    required String titulo,
    required String descripcion,
    String? urlRecurso,
  }) async {
    cursosEditados.add({'id': id, 'titulo': titulo, 'url': urlRecurso});
    return Curso(
      id: id,
      titulo: titulo,
      descripcion: descripcion,
      profesor: 'Marta Ruiz',
    );
  }

  @override
  Future<List<EntradaDeRanking>> ranking() async => rankingDevuelto;

  @override
  Future<List<Curso>> cursos() async => cursosDevueltos;

  @override
  Future<List<AlumnoDelCentro>> alumnosDelCentro(int idColegio) async =>
      alumnos;

  @override
  Future<Curso> crearCurso({
    required String titulo,
    required String descripcion,
    String? urlRecurso,
    required List<int> idsAlumnos,
  }) async {
    cursosCreados.add({'titulo': titulo, 'url': urlRecurso, 'ids': idsAlumnos});
    return Curso(
      id: 1,
      titulo: titulo,
      descripcion: descripcion,
      profesor: 'Marta Ruiz',
      alumnosAsignados: idsAlumnos.length,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Solo responde al estado de mercado: si la ficha del profesor pidiera cartera o
/// portafolio, `noSuchMethod` lo haría explotar.
class _ProyectosSoloMercado implements RepositorioProyectos {
  final pedidos = <String>[];

  @override
  Future<EstadoDeMercado> estadoDeMercado(int idProyecto) async {
    pedidos.add('mercado');
    return const EstadoDeMercado(
      idProyecto: 3,
      precioBase: 100,
      precioActual: 110,
      participacionesTotales: 100,
      participacionesEmitidas: 10,
      participacionesDisponibles: 90,
      recaudado: 1000,
      inversores: 1,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) {
    pedidos.add(invocation.memberName.toString());
    return super.noSuchMethod(invocation);
  }
}

class _SesionAbierta extends Sesion {
  _SesionAbierta(this.rol);

  final Rol rol;

  /// Lo que se le pidió cambiar, para comprobarlo sin servidor.
  final cambios = <Map<String, String?>>[];

  @override
  Future<Perfil> actualizarMiCuenta({
    required String passwordActual,
    String? email,
    String? passwordNueva,
  }) async {
    cambios.add({
      'actual': passwordActual,
      'email': email,
      'nueva': passwordNueva,
    });
    return state.perfil!;
  }

  @override
  EstadoDeSesion build() => EstadoDeSesion(
    perfil: Perfil(
      idUsuario: 1,
      email: 'yo@ies.example',
      rol: rol,
      profesor: rol == Rol.profesor
          ? const PerfilProfesor(
              id: 1,
              nombre: 'Marta',
              apellido: 'Ruiz',
              idColegio: 2,
              nombreColegio: 'IES Prueba',
            )
          : null,
      alumno: rol == Rol.alumno
          ? const PerfilAlumno(
              id: 7,
              nombre: 'Ana',
              apellido: 'Martinez',
              idColegio: 2,
              nombreColegio: 'IES Prueba',
              jicpInicial: 500000,
            )
          : null,
    ),
  );
}

Future<void> _montar(
  WidgetTester tester,
  Widget pantalla, {
  Rol rol = Rol.profesor,
  RepositorioAula? aula,
  RepositorioProyectos? proyectos,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        sesionProvider.overrideWith(() => _SesionAbierta(rol)),
        if (aula != null) repositorioAulaProvider.overrideWithValue(aula),
        if (proyectos != null)
          repositorioProyectosProvider.overrideWithValue(proyectos),
      ],
      child: MaterialApp(home: Scaffold(body: pantalla)),
    ),
  );
  await tester.pumpAndSettle();
}

EntradaDeRanking _fila(int posicion, String nombre, double rentabilidad) =>
    EntradaDeRanking(
      posicion: posicion,
      idAlumno: posicion,
      nombre: nombre,
      apellido: 'Prueba',
      saldo: 1000,
      valorParticipaciones: 0,
      patrimonio: 1000,
      rentabilidad: rentabilidad,
    );

void main() {
  test('los modelos leen las respuestas de la API', () {
    final curso = Curso.desdeJson({
      'id': 4,
      'titulo': 'Finanzas',
      'descripcion': 'Curso',
      'urlRecurso': null,
      'profesor': 'Marta Ruiz',
      'alumnosAsignados': null,
      'progreso': 100.00,
      'fechaRegistro': '2026-10-08T10:00:00',
    });
    expect(curso.completado, isTrue);
    expect(curso.alumnosAsignados, isNull);

    final fila = EntradaDeRanking.desdeJson({
      'posicion': 1,
      'idAlumno': 9,
      'nombre': 'Ana',
      'apellido': 'Martinez',
      'saldo': 499000.00,
      'valorParticipaciones': 1100.00,
      'patrimonio': 500100.00,
      'saldoInicial': 500000.00,
      'rentabilidad': 0.02,
    });
    expect(fila.nombreCompleto, 'Ana Martinez');
    expect(fila.rentabilidad, 0.02);
  });

  group('ranking', () {
    testWidgets('pinta el orden del servidor', (tester) async {
      final aula = _AulaFalsa()
        ..rankingDevuelto = [_fila(1, 'Ana', 12.5), _fila(2, 'Diego', -3)];
      await _montar(tester, const RankingScreen(busqueda: ''), aula: aula);

      expect(find.text('#1'), findsOneWidget);
      expect(find.text('Ana Prueba'), findsOneWidget);
      expect(find.text('+12.50%'), findsOneWidget);
      expect(find.text('-3.00%'), findsOneWidget);
    });

    testWidgets('buscar filtra sin cambiar la posición de nadie', (
      tester,
    ) async {
      final aula = _AulaFalsa()
        ..rankingDevuelto = [_fila(1, 'Ana', 12.5), _fila(2, 'Diego', -3)];
      await _montar(tester, const RankingScreen(busqueda: 'die'), aula: aula);

      expect(find.text('Ana Prueba'), findsNothing);
      expect(find.text('Diego Prueba'), findsOneWidget);
      expect(find.text('#2'), findsOneWidget);
    });
  });

  group('crear curso', () {
    final alumnos = [
      const AlumnoDelCentro(id: 7, nombre: 'Ana', apellido: 'Martinez'),
      const AlumnoDelCentro(id: 8, nombre: 'Diego', apellido: 'Prueba'),
    ];

    Future<void> rellenar(WidgetTester tester, {String url = ''}) async {
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Título del curso'),
        'Finanzas',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Descripción'),
        'Aprende a ahorrar',
      );
      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'Enlace a vídeo o documento (opcional)',
        ),
        url,
      );
    }

    Future<void> publicar(WidgetTester tester) async {
      final boton = find.byType(FilledButton);
      await tester.ensureVisible(boton);
      // ensureVisible hace scroll animado: hay que esperar a que acabe antes de pulsar.
      await tester.pumpAndSettle();
      await tester.tap(boton);
      await tester.pumpAndSettle();
    }

    testWidgets('sin alumnos elegidos no se envía', (tester) async {
      final aula = _AulaFalsa()..alumnos = alumnos;
      await _montar(tester, const CrearCursoScreen(), aula: aula);

      await rellenar(tester);
      await publicar(tester);

      expect(find.text('Elige al menos un alumno'), findsOneWidget);
      expect(aula.cursosCreados, isEmpty);
    });

    testWidgets('"Todos" asigna el curso a toda la clase', (tester) async {
      final aula = _AulaFalsa()..alumnos = alumnos;
      await _montar(tester, const CrearCursoScreen(), aula: aula);

      await rellenar(tester, url: 'https://ejemplo.org/video');
      await tester.tap(find.text('Todos'));
      await tester.pump();
      expect(find.text('Publicar y asignar a 2'), findsOneWidget);

      await publicar(tester);

      expect(aula.cursosCreados.single['titulo'], 'Finanzas');
      expect(aula.cursosCreados.single['url'], 'https://ejemplo.org/video');
      expect(aula.cursosCreados.single['ids'], unorderedEquals([7, 8]));
    });

    testWidgets('un enlace que no es http(s) no sale del móvil', (
      tester,
    ) async {
      final aula = _AulaFalsa()..alumnos = alumnos;
      await _montar(tester, const CrearCursoScreen(), aula: aula);

      await rellenar(tester, url: 'javascript:alert(1)');
      await tester.tap(find.text('Ana Martinez'));
      await publicar(tester);

      expect(
        find.text('Tiene que empezar por http:// o https://'),
        findsOneWidget,
      );
      expect(aula.cursosCreados, isEmpty);
    });
  });

  testWidgets('el profesor ve sus cursos con cuántos alumnos tiene asignados', (
    tester,
  ) async {
    final aula = _AulaFalsa()
      ..cursosDevueltos = [
        const Curso(
          id: 1,
          titulo: 'Finanzas',
          descripcion: 'x',
          profesor: 'Marta Ruiz',
          alumnosAsignados: 3,
        ),
      ];
    await _montar(tester, const CursosProfesorScreen(), aula: aula);

    expect(find.text('Finanzas'), findsOneWidget);
    expect(find.text('3 alumnos asignados'), findsOneWidget);
  });

  testWidgets('el alumno ve sus cursos con su progreso', (tester) async {
    final aula = _AulaFalsa()
      ..cursosDevueltos = [
        const Curso(
          id: 1,
          titulo: 'Finanzas',
          descripcion: 'x',
          profesor: 'Marta Ruiz',
          progreso: 40,
        ),
      ];
    await _montar(tester, const LearningScreen(), rol: Rol.alumno, aula: aula);

    expect(find.text('Finanzas'), findsOneWidget);
    expect(find.text('Con Marta Ruiz'), findsOneWidget);
    expect(find.text('40%'), findsOneWidget);
  });

  testWidgets(
    'la ficha del profesor es de solo lectura y no pide cartera ni portafolio',
    (tester) async {
      final proyectos = _ProyectosSoloMercado();
      await _montar(
        tester,
        const FichaProyectoScreen(
          proyecto: ProyectoDeMercado(
            id: 3,
            nombre: 'Huerto',
            descripcion: 'Verduras',
            idCategoria: 4,
            categoria: 'Educación',
            estado: 'Publicado',
            inversionInicial: 1000,
            precioBase: 100,
            participacionesTotales: 100,
            participacionesEmitidas: 10,
            idCreador: 7,
            nombreCreador: 'Ana Martinez',
          ),
        ),
        proyectos: proyectos,
      );

      expect(proyectos.pedidos, ['mercado']);
      expect(find.text('110.00 JICP'), findsOneWidget);

      // Al final de la ficha: los comentarios se pueden leer, pero no hay botón de
      // compra. Se baja hasta allí antes de comprobarlo; si no, "no está" sería
      // simplemente que la lista aún no lo ha construido.
      await tester.scrollUntilVisible(find.text('Comentarios'), 300);
      await tester.drag(find.byType(Scrollable).first, const Offset(0, -2000));
      await tester.pumpAndSettle();
      expect(find.text('Comentarios'), findsOneWidget);
      expect(
        find.text('Comprar participaciones', skipOffstage: false),
        findsNothing,
      );
    },
  );

  group('editar curso', () {
    const curso = Curso(
      id: 5,
      titulo: 'Finanzas',
      descripcion: 'Ahorro',
      profesor: 'Marta Ruiz',
      urlRecurso: 'https://ejemplo.org/viejo',
      alumnosAsignados: 3,
    );

    testWidgets('viene relleno, sin selector de alumnos, y guarda el contenido', (
      tester,
    ) async {
      final aula = _AulaFalsa();
      await _montar(tester, const CrearCursoScreen(curso: curso), aula: aula);

      expect(find.text('Editar Curso'), findsOneWidget);
      expect(find.text('Finanzas'), findsOneWidget);
      expect(find.text('Asignar a alumnos'), findsNothing);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Título del curso'),
        'Finanzas II',
      );
      await tester.enterText(
        find.widgetWithText(
          TextFormField,
          'Enlace a vídeo o documento (opcional)',
        ),
        '',
      );
      final boton = find.widgetWithText(FilledButton, 'Guardar cambios');
      await tester.ensureVisible(boton);
      // ensureVisible hace scroll animado: hay que esperar a que acabe antes de pulsar.
      await tester.pumpAndSettle();
      await tester.tap(boton);
      await tester.pumpAndSettle();

      expect(aula.cursosEditados.single, {
        'id': 5,
        'titulo': 'Finanzas II',
        'url': '',
      });
    });
  });

  group('editar perfil', () {
    Future<_SesionAbierta> montarPerfil(WidgetTester tester) async {
      final sesion = _SesionAbierta(Rol.profesor);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [sesionProvider.overrideWith(() => sesion)],
          child: const MaterialApp(home: EditarCuentaScreen()),
        ),
      );
      await tester.pumpAndSettle();
      return sesion;
    }

    Future<void> guardar(WidgetTester tester) async {
      final boton = find.widgetWithText(FilledButton, 'Guardar cambios');
      await tester.ensureVisible(boton);
      // ensureVisible hace scroll animado: hay que esperar a que acabe antes de pulsar.
      await tester.pumpAndSettle();
      await tester.tap(boton);
      await tester.pumpAndSettle();
    }

    testWidgets('enseña el nombre pero no deja editarlo', (tester) async {
      await montarPerfil(tester);

      expect(find.text('Marta Ruiz'), findsOneWidget);
      // El nombre no es un campo de texto: solo hay email y las tres contraseñas.
      expect(find.byType(TextFormField), findsNWidgets(4));
    });

    testWidgets('sin la contraseña actual no se envía nada', (tester) async {
      final sesion = await montarPerfil(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Email'),
        'nuevo@ies.example',
      );
      await guardar(tester);

      expect(find.text('Escribe tu contraseña actual'), findsOneWidget);
      expect(sesion.cambios, isEmpty);
    });

    testWidgets('cambia solo el email si la contraseña nueva va vacía', (
      tester,
    ) async {
      final sesion = await montarPerfil(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Email'),
        'Nuevo@IES.example',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña actual'),
        'la-de-siempre',
      );
      await guardar(tester);

      expect(sesion.cambios.single, {
        'actual': 'la-de-siempre',
        'email': 'nuevo@ies.example',
        'nueva': null,
      });
    });

    testWidgets('la contraseña nueva tiene que coincidir con la repetida', (
      tester,
    ) async {
      final sesion = await montarPerfil(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña nueva'),
        'otra-larga-1',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Repite la contraseña nueva'),
        'otra-larga-2',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña actual'),
        'la-de-siempre',
      );
      await guardar(tester);

      expect(find.text('No coincide con la nueva'), findsOneWidget);
      expect(sesion.cambios, isEmpty);
    });

    testWidgets('sin cambios avisa en vez de llamar al servidor', (
      tester,
    ) async {
      final sesion = await montarPerfil(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña actual'),
        'la-de-siempre',
      );
      await guardar(tester);

      expect(find.text('No has cambiado nada'), findsOneWidget);
      expect(sesion.cambios, isEmpty);
    });
  });
}
