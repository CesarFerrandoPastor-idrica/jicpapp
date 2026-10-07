import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Guarda **solo los tokens**, en el almacen cifrado del sistema (Keystore en
/// Android, Keychain en iOS).
///
/// Aqui no se guarda ningun saldo, ni cartera, ni nota. Eso es del servidor: lo
/// que la app tiene de esos datos es una copia de lectura que se tira y se
/// vuelve a pedir. Guardarlos en disco los convertiria en algo editable con un
/// movil rooteado, y el saldo dejaria de significar nada.
class AlmacenDeTokens {
  AlmacenDeTokens([FlutterSecureStorage? almacen])
      : _almacen = almacen ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _almacen;

  static const _claveAccess = 'jicp_access_token';
  static const _claveRefresh = 'jicp_refresh_token';

  Future<String?> leerAccessToken() => _almacen.read(key: _claveAccess);

  Future<String?> leerRefreshToken() => _almacen.read(key: _claveRefresh);

  Future<void> guardar({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _almacen.write(key: _claveAccess, value: accessToken);
    await _almacen.write(key: _claveRefresh, value: refreshToken);
  }

  Future<void> borrar() async {
    await _almacen.delete(key: _claveAccess);
    await _almacen.delete(key: _claveRefresh);
  }
}
