/// Una entrada de catálogo (categoría, estado o rol). El backend las devuelve
/// todas con la misma forma.
class Catalogo {
  const Catalogo({required this.id, required this.nombre, this.descripcion});

  final int id;
  final String nombre;
  final String? descripcion;

  factory Catalogo.desdeJson(Map<String, dynamic> json) => Catalogo(
        id: json['id'] as int,
        nombre: json['nombre'] as String,
        descripcion: json['descripcion'] as String?,
      );
}

/// Ids de `estado_proyecto`, fijados en la migración V2.
///
/// Se usan como literales porque el catálogo los tiene fijos y la app necesita
/// poder decir "publicado" sin traerse la fila.
class EstadosProyecto {
  const EstadosProyecto._();

  static const int borrador = 1;
  static const int publicado = 2;
  static const int cerrado = 3;
}

/// Lo que devuelve el servidor tras crear un proyecto.
///
/// Trae ya los datos de mercado para poder enseñar el resultado real de la
/// operación —cuántas participaciones se emitieron y a qué precio queda— sin
/// tener que encadenar una segunda llamada.
class ProyectoCreado {
  const ProyectoCreado({
    required this.id,
    required this.nombre,
    required this.categoria,
    required this.estado,
    required this.inversionInicial,
    required this.precioBase,
    required this.participacionesTotales,
    required this.participacionesEmitidas,
  });

  final int id;
  final String nombre;
  final String categoria;
  final String estado;
  final double inversionInicial;
  final double precioBase;
  final double participacionesTotales;

  /// Las que se ha llevado el fundador por su inversión inicial.
  final double participacionesEmitidas;

  /// Precio que pagará el siguiente inversor, ya subido por la propia fundación.
  ///
  /// Se recalcula igual que en el servidor —`base × (1 + emitidas / totales)`—
  /// solo para poder enseñarlo en la pantalla de confirmación. **No es una fuente
  /// de verdad**: el precio que se cobra lo decide siempre el servidor.
  double get precioSiguiente =>
      precioBase * (1 + participacionesEmitidas / participacionesTotales);

  factory ProyectoCreado.desdeJson(Map<String, dynamic> json) => ProyectoCreado(
        id: json['id'] as int,
        nombre: json['nombre'] as String,
        categoria: (json['categoria'] as Map<String, dynamic>)['nombre'] as String,
        estado: (json['estado'] as Map<String, dynamic>)['nombre'] as String,
        inversionInicial: (json['inversionInicial'] as num).toDouble(),
        precioBase: (json['precioBase'] as num).toDouble(),
        participacionesTotales:
            (json['participacionesTotales'] as num).toDouble(),
        participacionesEmitidas:
            (json['participacionesEmitidas'] as num).toDouble(),
      );
}

/// Un proyecto en el que participa el alumno, junto al papel que desempeña.
///
/// El creador no es una columna de `proyecto`: es la fila de `alumno_proyecto`
/// con rol *Creador*. Por eso este modelo trae el rol al lado del proyecto en
/// lugar de un simple booleano "es mío".
class ProyectoConRol {
  const ProyectoConRol({
    required this.rol,
    required this.idProyecto,
    required this.nombre,
    required this.categoria,
    required this.estado,
    required this.inversionInicial,
    required this.precioBase,
    required this.participacionesTotales,
    required this.participacionesEmitidas,
  });

  /// "Creador", "Socio" o "Colaborador".
  final String rol;

  final int idProyecto;
  final String nombre;
  final String categoria;
  final String estado;
  final double inversionInicial;
  final double precioBase;
  final double participacionesTotales;
  final double participacionesEmitidas;

  bool get soyElCreador => rol == 'Creador';

  /// Precio actual, con la misma fórmula que el servidor. Solo para mostrar.
  double get precioActual => participacionesTotales == 0
      ? precioBase
      : precioBase * (1 + participacionesEmitidas / participacionesTotales);

  /// Cuánto se ha colocado de la ronda, de 0 a 1.
  double get progresoDeRonda => participacionesTotales == 0
      ? 0
      : (participacionesEmitidas / participacionesTotales).clamp(0.0, 1.0);

  factory ProyectoConRol.desdeJson(Map<String, dynamic> json) {
    final p = json['proyecto'] as Map<String, dynamic>;
    return ProyectoConRol(
      rol: (json['rol'] as Map<String, dynamic>)['nombre'] as String,
      idProyecto: p['id'] as int,
      nombre: p['nombre'] as String,
      categoria: (p['categoria'] as Map<String, dynamic>)['nombre'] as String,
      estado: (p['estado'] as Map<String, dynamic>)['nombre'] as String,
      inversionInicial: (p['inversionInicial'] as num).toDouble(),
      precioBase: (p['precioBase'] as num).toDouble(),
      participacionesTotales: (p['participacionesTotales'] as num).toDouble(),
      participacionesEmitidas: (p['participacionesEmitidas'] as num).toDouble(),
    );
  }
}

/// Un proyecto tal y como aparece en el mercado del centro.
class ProyectoDeMercado {
  const ProyectoDeMercado({
    required this.id,
    required this.nombre,
    required this.descripcion,
    required this.idCategoria,
    required this.categoria,
    required this.estado,
    required this.inversionInicial,
    required this.precioBase,
    required this.participacionesTotales,
    required this.participacionesEmitidas,
    this.idCreador,
    this.nombreCreador,
  });

  final int id;
  final String nombre;
  final String descripcion;
  final int idCategoria;
  final String categoria;
  final String estado;
  final double inversionInicial;
  final double precioBase;
  final double participacionesTotales;
  final double participacionesEmitidas;

  /// Id de alumno del fundador. Puede faltar en proyectos antiguos sin creador.
  final int? idCreador;
  final String? nombreCreador;

  /// Precio actual con la fórmula del servidor, **solo para pintar la tarjeta**.
  ///
  /// La compra no lo usa: el precio que se envía como esperado sale de
  /// `/proyectos/{id}/mercado`, porque el servidor lo compara al céntimo de
  /// céntimo y un cálculo en coma flotante podría no coincidir.
  double get precioOrientativo => participacionesTotales == 0
      ? precioBase
      : precioBase * (1 + participacionesEmitidas / participacionesTotales);

  double get progresoDeRonda => participacionesTotales == 0
      ? 0
      : (participacionesEmitidas / participacionesTotales).clamp(0.0, 1.0);

  factory ProyectoDeMercado.desdeJson(Map<String, dynamic> json) {
    final categoria = json['categoria'] as Map<String, dynamic>;
    final creador = json['creador'] as Map<String, dynamic>?;
    return ProyectoDeMercado(
      id: json['id'] as int,
      nombre: json['nombre'] as String,
      descripcion: json['descripcion'] as String,
      idCategoria: categoria['id'] as int,
      categoria: categoria['nombre'] as String,
      estado: (json['estado'] as Map<String, dynamic>)['nombre'] as String,
      inversionInicial: (json['inversionInicial'] as num).toDouble(),
      precioBase: (json['precioBase'] as num).toDouble(),
      participacionesTotales: (json['participacionesTotales'] as num).toDouble(),
      participacionesEmitidas:
          (json['participacionesEmitidas'] as num).toDouble(),
      idCreador: creador?['idAlumno'] as int?,
      nombreCreador: creador == null
          ? null
          : '${creador['nombre']} ${creador['apellido']}',
    );
  }
}

/// Estado del mercado de un proyecto en este instante, calculado por el servidor.
class EstadoDeMercado {
  const EstadoDeMercado({
    required this.idProyecto,
    required this.precioBase,
    required this.precioActual,
    required this.participacionesTotales,
    required this.participacionesEmitidas,
    required this.participacionesDisponibles,
    required this.recaudado,
    required this.inversores,
  });

  final int idProyecto;
  final double precioBase;

  /// El precio al que se compraría ahora. Es el que se envía como
  /// `precioUnitarioEsperado`: si alguien compra antes y lo mueve, el servidor
  /// rechaza la compra en vez de cobrar un precio que el alumno no vio.
  final double precioActual;
  final double participacionesTotales;
  final double participacionesEmitidas;
  final double participacionesDisponibles;

  /// Lo que hay en la tesorería del proyecto.
  final double recaudado;
  final int inversores;

  bool get rondaAgotada => participacionesDisponibles <= 0;

  factory EstadoDeMercado.desdeJson(Map<String, dynamic> json) =>
      EstadoDeMercado(
        idProyecto: json['idProyecto'] as int,
        precioBase: (json['precioBase'] as num).toDouble(),
        precioActual: (json['precioActual'] as num).toDouble(),
        participacionesTotales:
            (json['participacionesTotales'] as num).toDouble(),
        participacionesEmitidas:
            (json['participacionesEmitidas'] as num).toDouble(),
        participacionesDisponibles:
            (json['participacionesDisponibles'] as num).toDouble(),
        recaudado: (json['recaudado'] as num).toDouble(),
        inversores: json['inversores'] as int,
      );
}

/// El recibo de una compra. Todo lo que trae lo ha calculado el servidor.
class ReciboDeInversion {
  const ReciboDeInversion({
    required this.idOperacion,
    required this.nombreProyecto,
    required this.participacionesCompradas,
    required this.precioUnitario,
    required this.importe,
    required this.participacionesTotales,
    required this.precioMedio,
    required this.precioSiguiente,
    required this.saldoCartera,
  });

  final int idOperacion;
  final String nombreProyecto;
  final double participacionesCompradas;
  final double precioUnitario;
  final double importe;

  /// Las que tiene ahora el alumno en el proyecto, sumando las de antes.
  final double participacionesTotales;
  final double precioMedio;

  /// A cuánto queda la siguiente, ya subida por esta misma compra.
  final double precioSiguiente;

  /// Saldo real tras la compra. **Este** es el que se pinta.
  final double saldoCartera;

  factory ReciboDeInversion.desdeJson(Map<String, dynamic> json) =>
      ReciboDeInversion(
        idOperacion: json['idOperacion'] as int,
        nombreProyecto: json['nombreProyecto'] as String,
        participacionesCompradas:
            (json['participacionesCompradas'] as num).toDouble(),
        precioUnitario: (json['precioUnitario'] as num).toDouble(),
        importe: (json['importe'] as num).toDouble(),
        participacionesTotales:
            (json['participacionesTotales'] as num).toDouble(),
        precioMedio: (json['precioMedio'] as num).toDouble(),
        precioSiguiente: (json['precioSiguiente'] as num).toDouble(),
        saldoCartera: (json['saldoCartera'] as num).toDouble(),
      );
}

/// Un comentario del hilo de un proyecto.
class Comentario {
  const Comentario({
    required this.id,
    required this.idAlumno,
    required this.autor,
    required this.texto,
    required this.fecha,
  });

  final int id;

  /// Quién lo escribió. Sirve para marcar los propios en el hilo.
  final int idAlumno;
  final String autor;
  final String texto;

  /// Hora del servidor (sin zona). Solo se usa para pintar "hace 5 min".
  final DateTime fecha;

  factory Comentario.desdeJson(Map<String, dynamic> json) => Comentario(
        id: json['id'] as int,
        idAlumno: json['idAlumno'] as int,
        autor: '${json['nombre']} ${json['apellido']}',
        texto: json['texto'] as String,
        fecha: DateTime.parse(json['fecha'] as String),
      );
}

/// Una página del hilo de comentarios, del más reciente al más antiguo.
class PaginaDeComentarios {
  const PaginaDeComentarios({
    required this.comentarios,
    required this.total,
    required this.esLaUltima,
  });

  final List<Comentario> comentarios;
  final int total;
  final bool esLaUltima;

  /// Lee la envoltura `Page` de Spring.
  factory PaginaDeComentarios.desdeJson(Map<String, dynamic> json) =>
      PaginaDeComentarios(
        comentarios: (json['content'] as List<dynamic>)
            .map((e) => Comentario.desdeJson(e as Map<String, dynamic>))
            .toList(),
        total: json['totalElements'] as int,
        esLaUltima: json['last'] as bool,
      );
}

/// Una posición del portafolio: participaciones compradas en un proyecto ajeno.
class Posicion {
  const Posicion({
    required this.idProyecto,
    required this.nombreProyecto,
    required this.categoria,
    required this.estado,
    required this.participaciones,
    required this.precioMedio,
    required this.precioActual,
    required this.valorActual,
    required this.plusvalia,
  });

  final int idProyecto;
  final String nombreProyecto;
  final String categoria;
  final String estado;
  final double participaciones;

  /// Lo que se pagó de media por participación.
  final double precioMedio;
  final double precioActual;

  /// Participaciones × precio actual, calculado por el servidor.
  final double valorActual;

  /// Diferencia entre lo que vale y lo que costó. Negativa si pierde.
  final double plusvalia;

  /// Rentabilidad en porcentaje sobre el coste.
  double get rentabilidad {
    final coste = participaciones * precioMedio;
    return coste == 0 ? 0 : (plusvalia / coste) * 100;
  }

  factory Posicion.desdeJson(Map<String, dynamic> json) => Posicion(
        idProyecto: json['idProyecto'] as int,
        nombreProyecto: json['nombreProyecto'] as String,
        categoria: json['categoria'] as String,
        estado: json['estado'] as String,
        participaciones: (json['participaciones'] as num).toDouble(),
        precioMedio: (json['precioMedio'] as num).toDouble(),
        precioActual: (json['precioActual'] as num).toDouble(),
        valorActual: (json['valorActual'] as num).toDouble(),
        plusvalia: (json['plusvalia'] as num).toDouble(),
      );
}
