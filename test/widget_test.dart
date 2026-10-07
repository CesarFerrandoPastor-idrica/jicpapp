// Pruebas de la pantalla de acceso.
//
// Solo cubren la validacion del formulario, que ocurre ANTES de tocar la red:
// son deterministas y no necesitan ni backend levantado ni almacen seguro.
// La validacion real —la que decide si entras— vive en el servidor y se prueba
// alli (backend/src/test/kotlin/com/jicp/api/AutenticacionTest.kt). Esto de aqui
// solo comprueba que la app no moleste al usuario mandando peticiones inutiles.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:jicpapp/screens/login_screen.dart';

void main() {
  Future<void> montarLogin(WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: LoginScreen()));
  }

  testWidgets('la pantalla pide email y contraseña', (tester) async {
    await montarLogin(tester);

    expect(find.text('Iniciar Sesión'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Email'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Contraseña'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Acceder'), findsOneWidget);
  });

  testWidgets('no se envia nada con los campos vacios', (tester) async {
    await montarLogin(tester);

    await tester.tap(find.widgetWithText(FilledButton, 'Acceder'));
    await tester.pump();

    expect(find.text('Introduce tu email'), findsOneWidget);
    expect(find.text('Introduce tu contraseña'), findsOneWidget);
  });

  testWidgets('un email sin arroba no llega a salir del movil', (tester) async {
    await montarLogin(tester);

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Email'),
      'esto-no-es-un-email',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Contraseña'),
      'contrasena-larga',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Acceder'));
    await tester.pump();

    expect(find.text('Ese email no tiene buena pinta'), findsOneWidget);
  });

  testWidgets('la contraseña se puede mostrar y volver a ocultar',
      (tester) async {
    await montarLogin(tester);

    expect(find.byIcon(Icons.visibility_off), findsOneWidget);

    await tester.tap(find.byIcon(Icons.visibility_off));
    await tester.pump();
    expect(find.byIcon(Icons.visibility), findsOneWidget);

    await tester.tap(find.byIcon(Icons.visibility));
    await tester.pump();
    expect(find.byIcon(Icons.visibility_off), findsOneWidget);
  });
}
