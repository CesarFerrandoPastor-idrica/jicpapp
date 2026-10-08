import 'package:dio/dio.dart';

import 'cliente_api.dart';
import 'modelos_auth.dart';
import 'modelos_proyecto.dart';

/// Lo que la app puede pedirle al servidor sobre proyectos y su mercado.
class RepositorioProyectos {
  RepositorioProyectos({required ClienteApi cliente}) : _cliente = cliente;

  final ClienteApi _cliente;

  /// Catálogo de categorías. Se pide al servidor en lugar de tenerlo escrito en
  /// la app: el `idCategoria` que se envía al crear tiene que ser el de la base,
  /// y una lista escrita a mano se desincroniza en cuanto se añade una categoría.
  Future<List<Catalogo>> categorias() async {
    final Response<List<dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<List<dynamic>>('/categorias');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw const ErrorApi('No se han podido cargar las categorías');
    }
    return respuesta.data!
        .map((e) => Catalogo.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Crea un proyecto.
  ///
  /// El alumno creador **no se envía**: lo saca el servidor del token. Y la
  /// inversión inicial se descuenta de su cartera en la misma transacción, así
  /// que un `409` aquí suele significar que no le llega el saldo.
  Future<ProyectoCreado> crear({
    required String nombre,
    required String descripcion,
    required int idCategoria,
    required int idEstado,
    required double inversionInicial,
    required double precioBase,
    required double participacionesTotales,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.post<Map<String, dynamic>>(
        '/proyectos',
        data: {
          'nombre': nombre.trim(),
          'descripcion': descripcion.trim(),
          'idCategoria': idCategoria,
          'idEstado': idEstado,
          'inversionInicial': inversionInicial,
          'precioBase': precioBase,
          'participacionesTotales': participacionesTotales,
        },
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 201 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido crear el proyecto',
        codigo: respuesta.statusCode,
      );
    }
    return ProyectoCreado.desdeJson(respuesta.data!);
  }

  /// Proyectos en los que participa el alumno, con su rol en cada uno.
  ///
  /// Devuelve tambien aquellos en los que es socio o colaborador, no solo los que
  /// fundo: la autoria vive en `alumno_proyecto`, y el rol viene al lado para
  /// poder distinguirlos en pantalla.
  Future<List<ProyectoConRol>> misProyectos(int idAlumno) async {
    final Response<List<dynamic>> respuesta;
    try {
      respuesta =
          await _cliente.dio.get<List<dynamic>>('/alumnos/$idAlumno/proyectos');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se han podido cargar tus proyectos',
        codigo: respuesta.statusCode,
      );
    }
    return respuesta.data!
        .map((e) => ProyectoConRol.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Participaciones que el alumno tiene en proyectos de otros, valoradas a
  /// precio de hoy. La plusvalia la calcula el servidor.
  Future<List<Posicion>> portafolio() async {
    final Response<List<dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<List<dynamic>>('/portafolio');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido cargar tu portafolio',
        codigo: respuesta.statusCode,
      );
    }
    return respuesta.data!
        .map((e) => Posicion.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Proyectos publicados de un centro: lo que se puede comprar.
  ///
  /// Se pide una sola página grande en lugar de paginar: el mercado es de un
  /// aula, y así el buscador y el filtro por categoría trabajan sobre la lista
  /// entera sin ir al servidor en cada pulsación.
  Future<List<ProyectoDeMercado>> mercado({required int idColegio}) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<Map<String, dynamic>>(
        '/proyectos',
        queryParameters: {
          'idColegio': idColegio,
          'idEstado': EstadosProyecto.publicado,
          'size': 200,
        },
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido cargar el mercado',
        codigo: respuesta.statusCode,
      );
    }
    return (respuesta.data!['content'] as List<dynamic>)
        .map((e) => ProyectoDeMercado.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Precio actual, participaciones libres y recaudado de un proyecto.
  Future<EstadoDeMercado> estadoDeMercado(int idProyecto) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio
          .get<Map<String, dynamic>>('/proyectos/$idProyecto/mercado');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido consultar el precio',
        codigo: respuesta.statusCode,
      );
    }
    return EstadoDeMercado.desdeJson(respuesta.data!);
  }

  /// Compra participaciones de un proyecto.
  ///
  /// Se envía una **intención**: cuántas y a qué precio las vio el alumno. El
  /// importe y el saldo los calcula el servidor y vuelven en el recibo.
  ///
  /// La [claveIdempotencia] la decide quien llama, no este método: tiene que
  /// sobrevivir a los reintentos del mismo intento de compra.
  ///
  /// Un [ErrorApi] **sin código** significa que no llegó respuesta: la compra
  /// puede haberse hecho o no, y solo se sabe reintentando con la misma clave.
  Future<ReciboDeInversion> invertir({
    required int idProyecto,
    required double participaciones,
    required double precioUnitarioEsperado,
    required String claveIdempotencia,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.post<Map<String, dynamic>>(
        '/inversiones',
        data: {
          'idProyecto': idProyecto,
          'participaciones': participaciones,
          'precioUnitarioEsperado': precioUnitarioEsperado,
        },
        options: Options(headers: {'Idempotency-Key': claveIdempotencia}),
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido completar la compra',
        codigo: respuesta.statusCode,
      );
    }
    return ReciboDeInversion.desdeJson(respuesta.data!);
  }

  /// Una página del hilo de comentarios de un proyecto, los más recientes primero.
  Future<PaginaDeComentarios> comentarios(
    int idProyecto, {
    int pagina = 0,
    int tamano = 20,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<Map<String, dynamic>>(
        '/proyectos/$idProyecto/comentarios',
        queryParameters: {'page': pagina, 'size': tamano},
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se han podido cargar los comentarios',
        codigo: respuesta.statusCode,
      );
    }
    return PaginaDeComentarios.desdeJson(respuesta.data!);
  }

  /// Publica un comentario. El autor no se envía: lo saca el servidor del token,
  /// y es él quien comprueba que el proyecto sea del mismo centro.
  Future<Comentario> comentar(int idProyecto, String texto) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.post<Map<String, dynamic>>(
        '/proyectos/$idProyecto/comentarios',
        data: {'texto': texto.trim()},
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 201 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido publicar el comentario',
        codigo: respuesta.statusCode,
      );
    }
    return Comentario.desdeJson(respuesta.data!);
  }

  /// Saldo actual de la cartera. Solo lectura: el saldo lo mueve el servidor.
  Future<double> saldo() async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.get<Map<String, dynamic>>('/cartera');
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido consultar el saldo',
        codigo: respuesta.statusCode,
      );
    }
    return (respuesta.data!['saldo'] as num).toDouble();
  }

  String? _detalle(Response respuesta) {
    final cuerpo = respuesta.data;
    if (cuerpo is! Map) return null;
    final detalle = cuerpo['detail'];
    return detalle is String && detalle.isNotEmpty ? detalle : null;
  }
}
