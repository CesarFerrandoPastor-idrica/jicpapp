import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'modelos_auth.dart';
import 'proveedores.dart';

/// Lo que la app sabe de la sesion en un instante dado. Inmutable: cada cambio
/// es un estado nuevo, y Riverpod repinta a quien lo este mirando.
class EstadoDeSesion {
  const EstadoDeSesion({this.perfil, this.saldo, this.caducada = false});

  /// El usuario que ha entrado, o null si no hay sesion.
  final Perfil? perfil;

  /// Ultimo saldo conocido, o null si todavia no se ha pedido.
  ///
  /// Es **cache de solo lectura** de lo que dijo el servidor la ultima vez, nunca
  /// estado propio: no se calcula en local ni se persiste en disco. Tras cualquier
  /// operacion que mueva dinero hay que volver a pedirlo o tomarlo del recibo.
  final double? saldo;

  /// La sesion murio por su cuenta (el refresh dejo de valer), no porque el
  /// usuario saliera. La app lo escucha para volver a la pantalla de acceso.
  final bool caducada;

  bool get hayAlguienDentro => perfil != null;

  Rol? get rol => perfil?.rol;

  PerfilAlumno? get alumno => perfil?.alumno;

  /// El centro de quien ha entrado, sea alumno o profesor. Null para un admin.
  int? get idColegio => perfil?.alumno?.idColegio ?? perfil?.profesor?.idColegio;
}

/// La sesion activa: quien ha entrado, con que rol y su ultimo saldo.
class Sesion extends Notifier<EstadoDeSesion> {
  @override
  EstadoDeSesion build() => const EstadoDeSesion();

  Future<Perfil> entrar({
    required String email,
    required String password,
  }) async {
    final perfil = await ref
        .read(repositorioAuthProvider)
        .login(email: email, password: password);
    state = EstadoDeSesion(perfil: perfil);
    return perfil;
  }

  /// Reaprovecha los tokens guardados para no pedir la contraseña en cada
  /// arranque. Devuelve null si no habia sesion o si ya no vale.
  Future<Perfil?> recuperarSesionGuardada() async {
    final auth = ref.read(repositorioAuthProvider);
    if (!await auth.haySesionGuardada()) return null;
    try {
      final perfil = await auth.obtenerPerfil();
      state = EstadoDeSesion(perfil: perfil);
      return perfil;
    } on ErrorApi {
      await auth.logout();
      return null;
    }
  }

  /// Relee el saldo del servidor. Se llama tras cada operacion que mueva dinero.
  Future<double?> refrescarSaldo() async {
    if (state.rol != Rol.alumno) return null;
    try {
      final saldo = await ref.read(repositorioProyectosProvider).saldo();
      state = EstadoDeSesion(perfil: state.perfil, saldo: saldo);
    } on ErrorApi {
      // Un fallo al refrescar no debe tumbar la pantalla: se conserva el ultimo
      // valor conocido y se reintentara en la siguiente operacion.
    }
    return state.saldo;
  }

  /// Guarda el saldo que acaba de devolver el servidor en el recibo de una
  /// operación. No es un cálculo local: es la misma respuesta del servidor,
  /// aprovechada para no tener que pedir `/cartera` otra vez.
  void anotarSaldoDelServidor(double saldo) {
    state = EstadoDeSesion(perfil: state.perfil, saldo: saldo);
  }

  /// Cambia el email y/o la contraseña de la cuenta.
  ///
  /// Si cambia la contraseña, el servidor revoca todas las sesiones, también la de
  /// este móvil. Para no echar al usuario, se vuelve a entrar en el acto con la
  /// contraseña nueva, que es justo la que acaba de escribir.
  Future<Perfil> actualizarMiCuenta({
    required String passwordActual,
    String? email,
    String? passwordNueva,
  }) async {
    final auth = ref.read(repositorioAuthProvider);
    final perfil = await auth.actualizarMiCuenta(
      passwordActual: passwordActual,
      email: email,
      passwordNueva: passwordNueva,
    );

    if (passwordNueva != null && passwordNueva.isNotEmpty) {
      final renovado = await auth.login(email: perfil.email, password: passwordNueva);
      state = EstadoDeSesion(perfil: renovado, saldo: state.saldo);
      return renovado;
    }

    state = EstadoDeSesion(perfil: perfil, saldo: state.saldo);
    return perfil;
  }

  Future<void> salir() async {
    await ref.read(repositorioAuthProvider).logout();
    state = const EstadoDeSesion();
  }

  /// La llama el cliente HTTP cuando el refresh deja de valer. No hay que
  /// llamarla desde las pantallas: para salir a proposito esta [salir].
  void sesionPerdida() {
    state = const EstadoDeSesion(caducada: true);
  }
}

final sesionProvider = NotifierProvider<Sesion, EstadoDeSesion>(Sesion.new);
