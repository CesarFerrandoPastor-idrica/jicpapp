/// Rol del usuario. Lo decide **el servidor**, no el selector de la pantalla de
/// login: el cliente no elige con que permisos entra.
enum Rol {
  alumno,
  profesor,
  admin;

  static Rol desdeApi(String valor) => switch (valor.toUpperCase()) {
        'ALUMNO' => Rol.alumno,
        'PROFESOR' => Rol.profesor,
        'ADMIN' => Rol.admin,
        _ => throw ArgumentError('Rol desconocido: $valor'),
      };

  String get etiqueta => switch (this) {
        Rol.alumno => 'Alumno',
        Rol.profesor => 'Profesor',
        Rol.admin => 'Administrador',
      };
}

/// Respuesta de `POST /auth/login` y `POST /auth/refresh`.
class Tokens {
  const Tokens({
    required this.accessToken,
    required this.refreshToken,
    required this.expiraEn,
    required this.rol,
    required this.idUsuario,
  });

  final String accessToken;
  final String refreshToken;

  /// Segundos de vida del access token.
  final int expiraEn;
  final Rol rol;
  final int idUsuario;

  factory Tokens.desdeJson(Map<String, dynamic> json) => Tokens(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        expiraEn: json['expiraEn'] as int,
        rol: Rol.desdeApi(json['rol'] as String),
        idUsuario: json['idUsuario'] as int,
      );
}

/// Datos de alumno que vienen dentro de `GET /yo`.
class PerfilAlumno {
  const PerfilAlumno({
    required this.id,
    required this.nombre,
    required this.apellido,
    required this.idColegio,
    required this.nombreColegio,
    required this.jicpInicial,
  });

  final int id;
  final String nombre;
  final String apellido;
  final int idColegio;
  final String nombreColegio;

  /// La concesion inicial, **no el saldo actual**. El saldo llegara de
  /// `/cartera` cuando exista el libro contable, y siempre desde el servidor.
  final double jicpInicial;

  String get nombreCompleto => '$nombre $apellido';

  factory PerfilAlumno.desdeJson(Map<String, dynamic> json) => PerfilAlumno(
        id: json['id'] as int,
        nombre: json['nombre'] as String,
        apellido: json['apellido'] as String,
        idColegio: json['idColegio'] as int,
        nombreColegio: json['nombreColegio'] as String,
        jicpInicial: (json['jicpInicial'] as num).toDouble(),
      );
}

/// Datos de profesor que vienen dentro de `GET /yo`.
class PerfilProfesor {
  const PerfilProfesor({
    required this.id,
    required this.nombre,
    required this.apellido,
    required this.idColegio,
    required this.nombreColegio,
  });

  final int id;
  final String nombre;
  final String apellido;

  /// El centro donde imparte. Acota lo que puede ver: alumnos, cursos y notas
  /// de su propio colegio y de ningun otro.
  final int idColegio;
  final String nombreColegio;

  String get nombreCompleto => '$nombre $apellido';

  factory PerfilProfesor.desdeJson(Map<String, dynamic> json) => PerfilProfesor(
        id: json['id'] as int,
        nombre: json['nombre'] as String,
        apellido: json['apellido'] as String,
        idColegio: json['idColegio'] as int,
        nombreColegio: json['nombreColegio'] as String,
      );
}

/// Respuesta de `GET /yo`: quien es el usuario del token.
class Perfil {
  const Perfil({
    required this.idUsuario,
    required this.email,
    required this.rol,
    this.alumno,
    this.profesor,
  });

  final int idUsuario;
  final String email;
  final Rol rol;

  /// Solo viene relleno cuando el rol es alumno.
  final PerfilAlumno? alumno;

  /// Solo viene relleno cuando el rol es profesor.
  final PerfilProfesor? profesor;

  String get nombreParaMostrar =>
      alumno?.nombreCompleto ?? profesor?.nombreCompleto ?? email;

  /// El centro al que pertenece, sea cual sea el rol. Null para un admin.
  String? get nombreColegio =>
      alumno?.nombreColegio ?? profesor?.nombreColegio;

  factory Perfil.desdeJson(Map<String, dynamic> json) => Perfil(
        idUsuario: json['idUsuario'] as int,
        email: json['email'] as String,
        rol: Rol.desdeApi(json['rol'] as String),
        alumno: json['alumno'] == null
            ? null
            : PerfilAlumno.desdeJson(json['alumno'] as Map<String, dynamic>),
        profesor: json['profesor'] == null
            ? null
            : PerfilProfesor.desdeJson(json['profesor'] as Map<String, dynamic>),
      );
}

/// Error de la API ya traducido a algo que se puede enseñar al usuario.
///
/// El backend responde en formato RFC 7807 (`application/problem+json`), asi que
/// el texto sale de ahi en lugar de inventarse en el cliente.
class ErrorApi implements Exception {
  const ErrorApi(this.mensaje, {this.codigo});

  final String mensaje;
  final int? codigo;

  bool get esCredencialesInvalidas => codigo == 401;

  @override
  String toString() => mensaje;
}
