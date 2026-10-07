import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;

/// Donde vive la API.
///
/// Se inyecta en compilacion para que no haya ninguna URL de produccion escrita
/// en el codigo:
///
/// ```
/// flutter run --dart-define=API_BASE_URL=http://192.168.1.227:8080/api/v1
/// ```
class ConfigApi {
  const ConfigApi._();

  static const String _definidaEnCompilacion =
      String.fromEnvironment('API_BASE_URL');

  /// La base de la API. Si no se inyecta nada, apunta al backend local.
  static String get urlBase {
    if (_definidaEnCompilacion.isNotEmpty) return _definidaEnCompilacion;
    return 'http://$_hostLocal:8080/api/v1';
  }

  /// `localhost` no significa lo mismo en todas partes: dentro del emulador de
  /// Android apunta al propio emulador, no al PC que lo aloja. La puerta al
  /// anfitrion es la 10.0.2.2. En el simulador de iOS, en web y en escritorio no
  /// hay esa indireccion.
  ///
  /// Se usa `defaultTargetPlatform` y no `Platform.isAndroid` porque este
  /// fichero tambien se compila para web, donde `dart:io` no existe: importarlo
  /// romperia la build de Chrome.
  static String get _hostLocal {
    if (kIsWeb) return 'localhost';
    return defaultTargetPlatform == TargetPlatform.android
        ? '10.0.2.2'
        : 'localhost';
  }
}
