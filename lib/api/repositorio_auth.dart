import 'package:dio/dio.dart';

import 'almacen_de_tokens.dart';
import 'cliente_api.dart';
import 'modelos_auth.dart';

/// Todo lo que la app puede pedirle al servidor sobre la sesion.
///
/// Las pantallas no hablan con dio directamente: llaman aqui y reciben modelos
/// o un [ErrorApi] ya presentable.
class RepositorioAuth {
  RepositorioAuth({required ClienteApi cliente, AlmacenDeTokens? almacen})
      : _cliente = cliente,
        _almacen = almacen ?? AlmacenDeTokens();

  final ClienteApi _cliente;
  final AlmacenDeTokens _almacen;

  /// Entra y guarda los tokens. Devuelve el perfil que dice **el servidor**.
  Future<Perfil> login({
    required String email,
    required String password,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {'email': email.trim(), 'password': password},
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        respuesta.statusCode == 401
            ? 'Email o contraseña incorrectos'
            : _detalle(respuesta) ?? 'No se ha podido iniciar sesion',
        codigo: respuesta.statusCode,
      );
    }

    final tokens = Tokens.desdeJson(respuesta.data!);
    await _almacen.guardar(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    );

    return obtenerPerfil();
  }

  /// `GET /yo`. El rol y la identidad salen del token, no de lo que diga la app.
  Future<Perfil> obtenerPerfil() async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<Map<String, dynamic>>('/yo');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido cargar el perfil',
        codigo: respuesta.statusCode,
      );
    }
    return Perfil.desdeJson(respuesta.data!);
  }

  /// Cambia el email y/o la contraseña del usuario del token. El servidor exige
  /// siempre la contraseña actual. Si cambia la contraseña, el servidor cierra
  /// todas las sesiones abiertas, esta incluida: quien llama tiene que volver a
  /// entrar con la nueva.
  Future<Perfil> actualizarMiCuenta({
    required String passwordActual,
    String? email,
    String? passwordNueva,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.patch<Map<String, dynamic>>(
        '/yo',
        data: {
          'passwordActual': passwordActual,
          if (email != null && email.trim().isNotEmpty) 'email': email.trim(),
          if (passwordNueva != null && passwordNueva.isNotEmpty) 'passwordNueva': passwordNueva,
        },
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se han podido guardar los cambios',
        codigo: respuesta.statusCode,
      );
    }
    return Perfil.desdeJson(respuesta.data!);
  }

  /// Cierra sesion. Revoca el refresh en el servidor y borra los tokens locales.
  ///
  /// Si la llamada falla igualmente se borran en local: no tiene sentido dejar a
  /// alguien dentro de la app porque el servidor no conteste.
  Future<void> logout() async {
    final refresh = await _almacen.leerRefreshToken();
    if (refresh != null) {
      try {
        await _cliente.dio.post('/auth/logout', data: {'refreshToken': refresh});
      } catch (_) {
        // Sin reintento: el token local se borra igual y el del servidor caduca solo.
      }
    }
    await _almacen.borrar();
  }

  /// ¿Hay tokens guardados de una sesion anterior?
  Future<bool> haySesionGuardada() async =>
      await _almacen.leerRefreshToken() != null;

  String? _detalle(Response respuesta) {
    final cuerpo = respuesta.data;
    if (cuerpo is! Map) return null;
    final detalle = cuerpo['detail'];
    return detalle is String && detalle.isNotEmpty ? detalle : null;
  }
}
