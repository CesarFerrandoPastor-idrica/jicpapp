import 'package:dio/dio.dart';

import 'almacen_de_tokens.dart';
import 'config_api.dart';
import 'modelos_auth.dart';

/// Cliente HTTP contra la API de JICP.
///
/// Concentra en un solo sitio tres cosas que si no acabarian repetidas y mal en
/// cada pantalla: poner el `Bearer`, renovar el token cuando caduca y traducir
/// los errores RFC 7807 a algo que se pueda enseñar.
class ClienteApi {
  ClienteApi({AlmacenDeTokens? almacen, Dio? dio})
      : _almacen = almacen ?? AlmacenDeTokens(),
        _dio = dio ?? Dio() {
    _dio.options
      ..baseUrl = ConfigApi.urlBase
      ..connectTimeout = const Duration(seconds: 10)
      ..receiveTimeout = const Duration(seconds: 10)
      ..contentType = Headers.jsonContentType
      // Los 4xx no son excepciones de transporte: se inspeccionan como respuesta
      // para poder leer el cuerpo problem+json.
      ..validateStatus = (codigo) => codigo != null && codigo < 500;

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: _alEnviar,
        onResponse: _alResponder,
      ),
    );
  }

  final Dio _dio;
  final AlmacenDeTokens _almacen;

  Dio get dio => _dio;

  /// Se invoca cuando la sesion muere de forma irrecuperable (el refresh ya no
  /// vale). La app debe volver a la pantalla de acceso.
  void Function()? alPerderLaSesion;

  /// Renovacion en curso, si la hay.
  ///
  /// **Imprescindible que sea una sola.** El servidor rota el refresh token en
  /// cada uso y trata un token ya rotado como robado: si dos peticiones
  /// caducadas intentasen renovar a la vez con el mismo token, la segunda
  /// pareceria un robo y el backend cerraria *todas* las sesiones del usuario.
  /// Compartiendo un unico Future, el resto espera al que ya esta en marcha.
  Future<bool>? _renovacionEnCurso;

  static const _rutasSinToken = {'/auth/login', '/auth/refresh', '/auth/logout'};

  Future<void> _alEnviar(
    RequestOptions opciones,
    RequestInterceptorHandler handler,
  ) async {
    if (!_rutasSinToken.contains(opciones.path)) {
      final token = await _almacen.leerAccessToken();
      if (token != null) {
        opciones.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(opciones);
  }

  Future<void> _alResponder(
    Response respuesta,
    ResponseInterceptorHandler handler,
  ) async {
    final peticion = respuesta.requestOptions;

    final esReintentable = respuesta.statusCode == 401 &&
        !_rutasSinToken.contains(peticion.path) &&
        peticion.extra['ya_reintentada'] != true;

    if (!esReintentable) {
      handler.next(respuesta);
      return;
    }

    final renovado = await _renovarToken();
    if (!renovado) {
      handler.next(respuesta);
      return;
    }

    // Se repite la peticion original una sola vez, ya con el token nuevo.
    peticion.extra['ya_reintentada'] = true;
    try {
      final reintento = await _dio.fetch(peticion);
      handler.resolve(reintento);
    } on DioException catch (e) {
      handler.next(e.response ?? respuesta);
    }
  }

  Future<bool> _renovarToken() {
    final enCurso = _renovacionEnCurso;
    if (enCurso != null) return enCurso;

    final nueva = _hacerRenovacion();
    _renovacionEnCurso = nueva;
    nueva.whenComplete(() => _renovacionEnCurso = null);
    return nueva;
  }

  Future<bool> _hacerRenovacion() async {
    final refresh = await _almacen.leerRefreshToken();
    if (refresh == null) return false;

    try {
      final respuesta = await _dio.post<Map<String, dynamic>>(
        '/auth/refresh',
        data: {'refreshToken': refresh},
      );

      if (respuesta.statusCode != 200 || respuesta.data == null) {
        await _cerrarSesionPorFuerza();
        return false;
      }

      final tokens = Tokens.desdeJson(respuesta.data!);
      await _almacen.guardar(
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
      );
      return true;
    } on DioException {
      await _cerrarSesionPorFuerza();
      return false;
    }
  }

  Future<void> _cerrarSesionPorFuerza() async {
    await _almacen.borrar();
    alPerderLaSesion?.call();
  }

  /// Convierte cualquier fallo en un [ErrorApi] con un texto presentable.
  ///
  /// El detalle sale del `problem+json` del servidor en lugar de inventarse aqui:
  /// asi el mensaje que ve el usuario y el que registra el backend son el mismo.
  static ErrorApi traducirError(Object error) {
    if (error is ErrorApi) return error;

    if (error is DioException) {
      final respuesta = error.response;
      if (respuesta != null) {
        return ErrorApi(
          _detalleDe(respuesta) ?? 'La operacion no se ha podido completar',
          codigo: respuesta.statusCode,
        );
      }
      return ErrorApi(
        switch (error.type) {
          DioExceptionType.connectionTimeout ||
          DioExceptionType.receiveTimeout ||
          DioExceptionType.sendTimeout =>
            'El servidor tarda demasiado en responder',
          DioExceptionType.connectionError =>
            'No se ha podido contactar con el servidor.\n'
                'Comprueba que la API esta levantada en ${ConfigApi.urlBase}',
          _ => 'Error de conexion con el servidor',
        },
      );
    }

    return const ErrorApi('Se ha producido un error inesperado');
  }

  static String? _detalleDe(Response respuesta) {
    final cuerpo = respuesta.data;
    if (cuerpo is! Map) return null;

    final detalle = cuerpo['detail'];
    if (detalle is String && detalle.isNotEmpty) return detalle;

    final titulo = cuerpo['title'];
    return titulo is String && titulo.isNotEmpty ? titulo : null;
  }
}
