// Pruebas del mercado que no necesitan backend.
//
// Cubren lo que decide el cliente: la Idempotency-Key, el parseo de las
// respuestas reales de la API, lo que la hoja de compra deja o no deja pulsar y
// como reacciona a cada respuesta del servidor. Para esto ultimo la hoja recibe un
// repositorio falso por Riverpod (`overrides`): no hay red de por medio.
//
// Que la compra sea correcta lo decide el servidor y se prueba alli
// (backend/src/test/kotlin/com/jicp/api/MercadoTest.kt).

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jicpapp/api/idempotencia.dart';
import 'package:jicpapp/api/modelos_auth.dart';
import 'package:jicpapp/api/modelos_proyecto.dart';
import 'package:jicpapp/api/proveedores.dart';
import 'package:jicpapp/api/repositorio_proyectos.dart';
import 'package:jicpapp/screens/ficha_proyecto_screen.dart';

/// Repositorio que contesta lo que el test le diga y apunta lo que le piden.
class _RepositorioFalso implements RepositorioProyectos {
  _RepositorioFalso({required this.estado});

  EstadoDeMercado estado;

  /// Una respuesta por compra, en orden. Cada una devuelve un recibo o lanza.
  final respuestas = <ReciboDeInversion Function()>[];

  final claves = <String>[];
  final cantidades = <double>[];
  final preciosEsperados = <double>[];

  @override
  Future<ReciboDeInversion> invertir({
    required int idProyecto,
    required double participaciones,
    required double precioUnitarioEsperado,
    required String claveIdempotencia,
  }) async {
    claves.add(claveIdempotencia);
    cantidades.add(participaciones);
    preciosEsperados.add(precioUnitarioEsperado);
    return respuestas.removeAt(0)();
  }

  @override
  Future<EstadoDeMercado> estadoDeMercado(int idProyecto) async => estado;

  // El resto de la API no lo usa la hoja de compra.
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

ReciboDeInversion _recibo() => const ReciboDeInversion(
      idOperacion: 1,
      nombreProyecto: 'Huerto',
      participacionesCompradas: 4,
      precioUnitario: 240,
      importe: 960,
      participacionesTotales: 4,
      precioMedio: 240,
      precioSiguiente: 256,
      saldoCartera: 40,
    );

void main() {
  group('Idempotency-Key', () {
    test('es un UUID v4', () {
      final uuidV4 = RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$');
      for (var i = 0; i < 100; i++) {
        expect(nuevaClaveDeIdempotencia(), matches(uuidV4));
      }
    });

    test('no se repite', () {
      final claves = {for (var i = 0; i < 1000; i++) nuevaClaveDeIdempotencia()};
      expect(claves, hasLength(1000));
    });

    test('cabe en la columna del servidor', () {
      expect(nuevaClaveDeIdempotencia().length, lessThanOrEqualTo(64));
    });
  });

  group('modelos', () {
    test('ProyectoDeMercado lee un elemento de GET /proyectos', () {
      final p = ProyectoDeMercado.desdeJson({
        'id': 12,
        'nombre': 'Huerto Urbano Escolar',
        'descripcion': 'Verduras en la azotea',
        'categoria': {'id': 4, 'nombre': 'Educación', 'descripcion': null},
        'estado': {'id': 2, 'nombre': 'Publicado', 'descripcion': null},
        'idColegio': 1,
        'nombreColegio': 'IES Ejemplo',
        'inversionInicial': 2000.00,
        'precioBase': 200.00,
        'participacionesTotales': 50.0000,
        'participacionesEmitidas': 10.0000,
        'creador': {'idAlumno': 7, 'nombre': 'Ana', 'apellido': 'Martinez'},
        'fechaRegistro': '2026-10-01T10:00:00',
      });

      expect(p.idCategoria, 4);
      expect(p.idCreador, 7);
      expect(p.nombreCreador, 'Ana Martinez');
      // 200 × (1 + 10/50) = 240, el ejemplo del README.
      expect(p.precioOrientativo, closeTo(240, 1e-9));
      expect(p.progresoDeRonda, closeTo(0.2, 1e-9));
    });

    test('un proyecto sin creador no rompe el mercado', () {
      final p = ProyectoDeMercado.desdeJson({
        'id': 1,
        'nombre': 'Antiguo',
        'descripcion': '-',
        'categoria': {'id': 1, 'nombre': 'Tecnología'},
        'estado': {'id': 2, 'nombre': 'Publicado'},
        'inversionInicial': 0,
        'precioBase': 100,
        'participacionesTotales': 100,
        'participacionesEmitidas': 0,
        'creador': null,
      });
      expect(p.idCreador, isNull);
      expect(p.nombreCreador, isNull);
    });

    test('ReciboDeInversion lee la respuesta de POST /inversiones', () {
      final r = ReciboDeInversion.desdeJson({
        'idOperacion': 8,
        'idProyecto': 12,
        'nombreProyecto': 'Huerto Urbano Escolar',
        'participacionesCompradas': 4.0000,
        'precioUnitario': 240.0000,
        'importe': 960.00,
        'participacionesTotales': 4.0000,
        'precioMedio': 240.0000,
        'precioSiguiente': 256.0000,
        'saldoCartera': 14040.00,
        'fecha': '2026-10-07T12:00:00',
      });
      expect(r.importe, 960);
      expect(r.saldoCartera, 14040);
    });
  });

  test('formatoParticipaciones quita los ceros de sobra', () {
    expect(formatoParticipaciones(4), '4');
    expect(formatoParticipaciones(2.5), '2.5');
    expect(formatoParticipaciones(0.1234), '0.1234');
    expect(formatoParticipaciones(40), '40');
  });

  group('hoja de compra', () {
    const proyecto = ProyectoDeMercado(
      id: 12,
      nombre: 'Huerto',
      descripcion: '-',
      idCategoria: 4,
      categoria: 'Educación',
      estado: 'Publicado',
      inversionInicial: 2000,
      precioBase: 200,
      participacionesTotales: 50,
      participacionesEmitidas: 10,
    );
    const mercado = EstadoDeMercado(
      idProyecto: 12,
      precioBase: 200,
      precioActual: 240,
      participacionesTotales: 50,
      participacionesEmitidas: 10,
      participacionesDisponibles: 40,
      recaudado: 2000,
      inversores: 1,
    );

    Future<void> montar(WidgetTester tester, {double? saldo = 1000}) =>
        tester.pumpWidget(ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: HojaDeCompra(proyecto: proyecto, mercado: mercado, saldo: saldo),
            ),
          ),
        ));

    FilledButton boton(WidgetTester tester) =>
        tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Confirmar compra'));

    testWidgets('sin cantidad no se puede comprar', (tester) async {
      await montar(tester);
      expect(boton(tester).onPressed, isNull);
    });

    testWidgets('calcula el importe con el precio del servidor', (tester) async {
      await montar(tester);
      await tester.enterText(find.byType(TextField), '4');
      await tester.pump();

      expect(find.text('960.00 JICP'), findsOneWidget);
      expect(boton(tester).onPressed, isNotNull);
    });

    testWidgets('acepta coma decimal', (tester) async {
      await montar(tester);
      await tester.enterText(find.byType(TextField), '2,5');
      await tester.pump();

      expect(find.text('600.00 JICP'), findsOneWidget);
      expect(boton(tester).onPressed, isNotNull);
    });

    testWidgets('no deja pedir más de lo que tiene de saldo', (tester) async {
      await montar(tester);
      await tester.enterText(find.byType(TextField), '5'); // 1200 > 1000
      await tester.pump();

      expect(find.textContaining('No te llega el saldo'), findsOneWidget);
      expect(boton(tester).onPressed, isNull);
    });

    testWidgets('no deja pedir más de lo que queda en la ronda', (tester) async {
      await montar(tester, saldo: null);
      await tester.enterText(find.byType(TextField), '41');
      await tester.pump();

      expect(find.textContaining('Solo quedan 40'), findsOneWidget);
      expect(boton(tester).onPressed, isNull);
    });

    testWidgets('rechaza más de 4 decimales', (tester) async {
      await montar(tester);
      await tester.enterText(find.byType(TextField), '1.23456');
      await tester.pump();

      expect(find.textContaining('hasta 4 decimales'), findsOneWidget);
      expect(boton(tester).onPressed, isNull);
    });
  });

  group('hoja de compra ante el servidor', () {
    const proyecto = ProyectoDeMercado(
      id: 12,
      nombre: 'Huerto',
      descripcion: '-',
      idCategoria: 4,
      categoria: 'Educación',
      estado: 'Publicado',
      inversionInicial: 2000,
      precioBase: 200,
      participacionesTotales: 50,
      participacionesEmitidas: 10,
    );
    const mercado = EstadoDeMercado(
      idProyecto: 12,
      precioBase: 200,
      precioActual: 240,
      participacionesTotales: 50,
      participacionesEmitidas: 10,
      participacionesDisponibles: 40,
      recaudado: 2000,
      inversores: 1,
    );

    /// Abre la hoja como en la app, en un bottom sheet, para poder ver lo que
    /// devuelve al cerrarse. El repositorio falso entra por `overrides`.
    Future<List<ReciboDeInversion?>> montar(
      WidgetTester tester,
      _RepositorioFalso repositorio,
    ) async {
      final cerradaCon = <ReciboDeInversion?>[];
      await tester.pumpWidget(ProviderScope(
        overrides: [repositorioProyectosProvider.overrideWithValue(repositorio)],
        child: MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  cerradaCon.add(await showModalBottomSheet<ReciboDeInversion>(
                    context: context,
                    isScrollControlled: true,
                    builder: (_) => const HojaDeCompra(
                      proyecto: proyecto,
                      mercado: mercado,
                      saldo: 5000,
                    ),
                  ));
                },
                child: const Text('abrir'),
              ),
            ),
          ),
        ),
      ));
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      return cerradaCon;
    }

    Future<void> pedir(WidgetTester tester, String cantidad) async {
      await tester.enterText(find.byType(TextField), cantidad);
      await tester.pump();
    }

    Future<void> pulsar(WidgetTester tester, String texto) async {
      await tester.tap(find.widgetWithText(FilledButton, texto));
      await tester.pumpAndSettle();
    }

    testWidgets('una compra correcta cierra la hoja con el recibo del servidor',
        (tester) async {
      final repo = _RepositorioFalso(estado: mercado)..respuestas.add(_recibo);
      final cerradaCon = await montar(tester, repo);

      await pedir(tester, '4');
      await pulsar(tester, 'Confirmar compra');

      expect(cerradaCon.single?.saldoCartera, 40);
      expect(repo.cantidades, [4]);
      // El precio esperado es el que dio el servidor, no uno calculado en la app.
      expect(repo.preciosEsperados, [240]);
    });

    testWidgets('sin respuesta se reintenta con la MISMA clave y la misma cantidad',
        (tester) async {
      final repo = _RepositorioFalso(estado: mercado)
        ..respuestas.add(() => throw const ErrorApi('Sin conexion'))
        ..respuestas.add(_recibo);
      final cerradaCon = await montar(tester, repo);

      await pedir(tester, '4');
      await pulsar(tester, 'Confirmar compra');

      // No se da por fallida: la hoja sigue abierta y solo deja reintentar.
      expect(cerradaCon, isEmpty);
      expect(find.textContaining('No sabemos si la compra llegó'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);

      await pulsar(tester, 'Reintentar compra');

      expect(repo.claves, hasLength(2));
      expect(repo.claves[1], repo.claves[0]);
      expect(repo.cantidades, [4, 4]);
      expect(cerradaCon.single, isNotNull);
    });

    testWidgets('un rechazo del servidor deja corregir y estrena clave',
        (tester) async {
      final repo = _RepositorioFalso(estado: mercado)
        ..respuestas.add(() => throw const ErrorApi(
              'El precio ha cambiado: viste 240 JICP y ahora es 256',
              codigo: 409,
            ))
        ..respuestas.add(_recibo);
      final cerradaCon = await montar(tester, repo);

      // Mientras el alumno decide, otro compra y el precio del servidor sube.
      repo.estado = const EstadoDeMercado(
        idProyecto: 12,
        precioBase: 200,
        precioActual: 256,
        participacionesTotales: 50,
        participacionesEmitidas: 14,
        participacionesDisponibles: 36,
        recaudado: 2960,
        inversores: 2,
      );

      await pedir(tester, '4');
      await pulsar(tester, 'Confirmar compra');

      expect(cerradaCon, isEmpty);
      expect(find.textContaining('El precio ha cambiado'), findsOneWidget);
      // Se ha releido el mercado: ya enseña el precio nuevo.
      expect(find.text('256.00 JICP'), findsOneWidget);
      // El campo sigue editable: la compra no se hizo, se puede corregir.
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isTrue);

      await pulsar(tester, 'Confirmar compra');

      expect(repo.claves, hasLength(2));
      expect(repo.claves[1], isNot(repo.claves[0]));
      expect(repo.preciosEsperados, [240, 256]);
      expect(cerradaCon.single, isNotNull);
    });
  });
}
