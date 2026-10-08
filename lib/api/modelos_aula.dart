/// Un curso. Para el profesor trae cuántos alumnos tiene asignados; para el alumno,
/// su progreso. El dato que no aplica viene a null.
class Curso {
  const Curso({
    required this.id,
    required this.titulo,
    required this.descripcion,
    required this.profesor,
    this.urlRecurso,
    this.alumnosAsignados,
    this.progreso,
  });

  final int id;
  final String titulo;
  final String descripcion;
  final String profesor;
  final String? urlRecurso;
  final int? alumnosAsignados;

  /// Porcentaje completado, de 0 a 100.
  final double? progreso;

  bool get completado => (progreso ?? 0) >= 100;

  factory Curso.desdeJson(Map<String, dynamic> json) => Curso(
        id: json['id'] as int,
        titulo: json['titulo'] as String,
        descripcion: json['descripcion'] as String,
        profesor: json['profesor'] as String,
        urlRecurso: json['urlRecurso'] as String?,
        alumnosAsignados: json['alumnosAsignados'] as int?,
        progreso: (json['progreso'] as num?)?.toDouble(),
      );
}

/// Un alumno en el ranking de su centro. Todo lo ha calculado el servidor.
class EntradaDeRanking {
  const EntradaDeRanking({
    required this.posicion,
    required this.idAlumno,
    required this.nombre,
    required this.apellido,
    required this.saldo,
    required this.valorParticipaciones,
    required this.patrimonio,
    required this.rentabilidad,
  });

  final int posicion;
  final int idAlumno;
  final String nombre;
  final String apellido;
  final double saldo;
  final double valorParticipaciones;
  final double patrimonio;

  /// Ganancia o pérdida frente a su saldo inicial, en porcentaje.
  final double rentabilidad;

  String get nombreCompleto => '$nombre $apellido';

  factory EntradaDeRanking.desdeJson(Map<String, dynamic> json) => EntradaDeRanking(
        posicion: json['posicion'] as int,
        idAlumno: json['idAlumno'] as int,
        nombre: json['nombre'] as String,
        apellido: json['apellido'] as String,
        saldo: (json['saldo'] as num).toDouble(),
        valorParticipaciones: (json['valorParticipaciones'] as num).toDouble(),
        patrimonio: (json['patrimonio'] as num).toDouble(),
        rentabilidad: (json['rentabilidad'] as num).toDouble(),
      );
}

/// Un alumno del centro, para elegir a quién se asigna un curso.
class AlumnoDelCentro {
  const AlumnoDelCentro({required this.id, required this.nombre, required this.apellido});

  final int id;
  final String nombre;
  final String apellido;

  String get nombreCompleto => '$nombre $apellido';

  factory AlumnoDelCentro.desdeJson(Map<String, dynamic> json) => AlumnoDelCentro(
        id: json['id'] as int,
        nombre: json['nombre'] as String,
        apellido: json['apellido'] as String,
      );
}
