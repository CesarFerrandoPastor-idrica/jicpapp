import 'package:dio/dio.dart';

import 'cliente_api.dart';
import 'modelos_auth.dart';
import 'modelos_aula.dart';

/// Lo que la app le pide al servidor sobre el aula: cursos, ranking y alumnado.
class RepositorioAula {
  RepositorioAula({required ClienteApi cliente}) : _cliente = cliente;

  final ClienteApi _cliente;

  /// Los cursos de quien pregunta: los que imparte un profesor, o los que tiene
  /// asignados un alumno. El servidor decide cuáles según el token.
  Future<List<Curso>> cursos() async {
    final respuesta = await _get<List<dynamic>>('/cursos', 'No se han podido cargar los cursos');
    return respuesta.map((e) => Curso.desdeJson(e as Map<String, dynamic>)).toList();
  }

  /// Crea un curso y lo asigna a los alumnos indicados. El profesor no se envía:
  /// sale del token, y el servidor comprueba que los alumnos sean de su centro.
  Future<Curso> crearCurso({
    required String titulo,
    required String descripcion,
    String? urlRecurso,
    required List<int> idsAlumnos,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.post<Map<String, dynamic>>(
        '/cursos',
        data: {
          'titulo': titulo.trim(),
          'descripcion': descripcion.trim(),
          if (urlRecurso != null && urlRecurso.trim().isNotEmpty) 'urlRecurso': urlRecurso.trim(),
          'idsAlumnos': idsAlumnos,
        },
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 201 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido crear el curso',
        codigo: respuesta.statusCode,
      );
    }
    return Curso.desdeJson(respuesta.data!);
  }

  /// Cambia el contenido de un curso. Solo funciona para el profesor que lo imparte.
  /// Los alumnos asignados no cambian. Un enlace vacío lo quita del curso.
  Future<Curso> actualizarCurso({
    required int id,
    required String titulo,
    required String descripcion,
    String? urlRecurso,
  }) async {
    final Response<Map<String, dynamic>> respuesta;
    try {
      respuesta = await _cliente.dio.put<Map<String, dynamic>>(
        '/cursos/$id',
        data: {
          'titulo': titulo.trim(),
          'descripcion': descripcion.trim(),
          if (urlRecurso != null && urlRecurso.trim().isNotEmpty) 'urlRecurso': urlRecurso.trim(),
        },
      );
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }

    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(
        _detalle(respuesta) ?? 'No se ha podido guardar el curso',
        codigo: respuesta.statusCode,
      );
    }
    return Curso.desdeJson(respuesta.data!);
  }

  /// Ranking del centro del profesor. El centro lo pone el servidor.
  Future<List<EntradaDeRanking>> ranking() async {
    final respuesta = await _get<List<dynamic>>('/ranking', 'No se ha podido cargar el ranking');
    return respuesta.map((e) => EntradaDeRanking.desdeJson(e as Map<String, dynamic>)).toList();
  }

  /// Alumnado de un centro, para asignar cursos. Una sola página grande: una
  /// clase cabe entera.
  Future<List<AlumnoDelCentro>> alumnosDelCentro(int idColegio) async {
    final pagina = await _get<Map<String, dynamic>>(
      '/alumnos',
      'No se ha podido cargar el alumnado',
      parametros: {'idColegio': idColegio, 'size': 200, 'sort': 'apellido,asc'},
    );
    return (pagina['content'] as List<dynamic>)
        .map((e) => AlumnoDelCentro.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<T> _get<T>(String ruta, String siFalla, {Map<String, dynamic>? parametros}) async {
    final Response<T> respuesta;
    try {
      respuesta = await _cliente.dio.get<T>(ruta, queryParameters: parametros);
    } catch (e) {
      throw ClienteApi.traducirError(e);
    }
    if (respuesta.statusCode != 200 || respuesta.data == null) {
      throw ErrorApi(_detalle(respuesta) ?? siFalla, codigo: respuesta.statusCode);
    }
    return respuesta.data as T;
  }

  String? _detalle(Response respuesta) {
    final cuerpo = respuesta.data;
    if (cuerpo is! Map) return null;
    final detalle = cuerpo['detail'];
    return detalle is String && detalle.isNotEmpty ? detalle : null;
  }
}
