import 'package:flutter/foundation.dart';

import 'cliente_api.dart';
import 'modelos_auth.dart';
import 'repositorio_auth.dart';
import 'repositorio_proyectos.dart';

/// La sesion activa: quien ha entrado y con que rol.
///
/// Es un [ChangeNotifier] suelto y no Riverpod ni BLoC a proposito. La eleccion
/// de gestor de estado sigue abierta (ver README) y meter uno aqui la daria por
/// tomada solo para poder guardar un perfil. Todo lo que hay debajo — cliente,
/// repositorio, almacen — es agnostico, asi que migrarlo despues es envolverlo,
/// no reescribirlo.
class Sesion extends ChangeNotifier {
  Sesion._() {
    _cliente.alPerderLaSesion = _alPerderLaSesion;
  }

  /// Instancia unica. La app no tiene todavia inyeccion de dependencias.
  static final Sesion instancia = Sesion._();

  final ClienteApi _cliente = ClienteApi();
  late final RepositorioAuth _repositorio = RepositorioAuth(cliente: _cliente);

  /// Acceso al resto de la API para las pantallas. Comparte el mismo cliente, y
  /// por tanto el mismo interceptor de token y de renovacion.
  late final RepositorioProyectos proyectos =
      RepositorioProyectos(cliente: _cliente);

  Perfil? _perfil;

  double? _saldo;

  /// Ultimo saldo conocido, o null si todavia no se ha pedido.
  ///
  /// Es **cache de solo lectura** de lo que dijo el servidor la ultima vez, nunca
  /// estado propio: no se calcula en local ni se persiste en disco. Tras cualquier
  /// operacion que mueva dinero hay que volver a pedirlo.
  double? get saldo => _saldo;

  /// El usuario que ha entrado, o null si no hay sesion.
  Perfil? get perfil => _perfil;

  bool get hayAlguienDentro => _perfil != null;

  Rol? get rol => _perfil?.rol;

  /// Se dispara cuando el servidor invalida la sesion por su cuenta (el refresh
  /// dejo de valer). La app lo usa para volver a la pantalla de acceso.
  VoidCallback? alExpirarLaSesion;

  Future<Perfil> entrar({
    required String email,
    required String password,
  }) async {
    final perfil = await _repositorio.login(email: email, password: password);
    _perfil = perfil;
    notifyListeners();
    return perfil;
  }

  /// Relee el saldo del servidor. Se llama tras cada operacion que mueva dinero.
  Future<double?> refrescarSaldo() async {
    if (_perfil?.rol != Rol.alumno) return null;
    try {
      _saldo = await proyectos.saldo();
      notifyListeners();
    } on ErrorApi {
      // Un fallo al refrescar no debe tumbar la pantalla: se conserva el ultimo
      // valor conocido y se reintentara en la siguiente operacion.
    }
    return _saldo;
  }

  Future<void> salir() async {
    await _repositorio.logout();
    _perfil = null;
    _saldo = null;
    notifyListeners();
  }

  /// Reaprovecha los tokens guardados para no pedir la contraseña en cada
  /// arranque. Devuelve null si no habia sesion o si ya no vale.
  Future<Perfil?> recuperarSesionGuardada() async {
    if (!await _repositorio.haySesionGuardada()) return null;
    try {
      final perfil = await _repositorio.obtenerPerfil();
      _perfil = perfil;
      notifyListeners();
      return perfil;
    } on ErrorApi {
      await _repositorio.logout();
      return null;
    }
  }

  void _alPerderLaSesion() {
    _perfil = null;
    _saldo = null;
    notifyListeners();
    alExpirarLaSesion?.call();
  }
}
