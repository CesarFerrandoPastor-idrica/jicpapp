import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api/modelos_auth.dart';
import 'api/sesion.dart';
import 'screens/main_navigation.dart';
import 'screens/teacher_navigation.dart';
import 'screens/welcome_screen.dart';

void main() {
  // ProviderScope guarda las instancias de todos los proveedores (cliente,
  // repositorios, sesion). Por debajo de el, cualquier pantalla puede pedirlas.
  runApp(const ProviderScope(child: MyApp()));
}

/// Necesario para poder navegar desde fuera del arbol de widgets: cuando el
/// servidor invalida la sesion, quien se entera es el interceptor de red, que no
/// tiene ningun BuildContext a mano.
final GlobalKey<NavigatorState> navegadorGlobal = GlobalKey<NavigatorState>();

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  /// La sesion ha muerto por su cuenta (el refresh dejo de valer, o el servidor
  /// la corto al detectar un token reutilizado). Se saca al usuario en vez de
  /// dejarlo en una pantalla que ya no puede cargar nada.
  void _volverAlAcceso() {
    final navegador = navegadorGlobal.currentState;
    if (navegador == null) return;

    navegador.pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const WelcomeScreen()),
      (route) => false,
    );
    final messenger = ScaffoldMessenger.maybeOf(navegador.context);
    messenger?.showSnackBar(
      const SnackBar(
        content: Text('Tu sesión ha caducado. Vuelve a iniciar sesión.'),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Solo reacciona a la caducidad, no a cualquier cierre: cuando el usuario
    // sale a proposito, la pantalla desde la que sale ya hace la navegacion.
    ref.listen(sesionProvider.select((s) => s.caducada), (antes, ahora) {
      if (ahora && antes != true) _volverAlAcceso();
    });

    return MaterialApp(
      title: 'JICP Bolsa Social',
      debugShowCheckedModeBanner: false,
      navigatorKey: navegadorGlobal,
      themeMode: ThemeMode.dark, // Forzamos modo oscuro
      darkTheme: ThemeData(
        useMaterial3: true,
        // Usamos Roboto o la fuente nativa del sistema, pero bien contrastada
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFD4AF37), // Dorado
          onPrimary: Colors.black,
          secondary: Color(0xFFB8901D),
          surface: Color(0xFF151515), // Gris carbón
          surfaceContainerHighest: Color(0xFF222222), // Tarjetas elevadas
          onSurface: Colors.white,
          onSurfaceVariant: Colors.white70,
        ),
        scaffoldBackgroundColor: const Color(0xFF0A0A0A), // Fondo principal casi negro
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.transparent,
          elevation: 0,
          iconTheme: IconThemeData(color: Color(0xFFD4AF37)), // Iconos dorados
          titleTextStyle: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
        ),
        dividerColor: Colors.white12,
      ),
      theme: ThemeData( // Fallback por si acaso
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0A0A0A),
      ),
      home: const _Arranque(),
    );
  }
}

/// Decide la primera pantalla segun haya o no una sesion reutilizable.
///
/// El rol que manda es el que devuelve `GET /yo`, no nada que estuviera guardado
/// en el movil: si alguien manipulase el almacen local, el servidor seguiria
/// diciendo la verdad y la app le llevaria a donde le corresponde.
class _Arranque extends ConsumerStatefulWidget {
  const _Arranque();

  @override
  ConsumerState<_Arranque> createState() => _ArranqueState();
}

class _ArranqueState extends ConsumerState<_Arranque> {
  late final Future<Perfil?> _sesionGuardada =
      ref.read(sesionProvider.notifier).recuperarSesionGuardada();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Perfil?>(
      future: _sesionGuardada,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(
            backgroundColor: Color(0xFF0A0A0A),
            body: Center(
              child: CircularProgressIndicator(color: Color(0xFFD4AF37)),
            ),
          );
        }

        // Sin sesion, o con una que el servidor ya no reconoce: a la portada.
        // Un fallo de red tambien cae aqui, y es lo correcto: sin poder
        // confirmar quien eres, la app no da por buena ninguna sesion.
        return switch (snapshot.data?.rol) {
          Rol.alumno => const MainNavigation(),
          Rol.profesor || Rol.admin => const TeacherNavigation(),
          null => const WelcomeScreen(),
        };
      },
    );
  }
}
