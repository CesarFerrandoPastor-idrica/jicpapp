import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'almacen_de_tokens.dart';
import 'cliente_api.dart';
import 'repositorio_aula.dart';
import 'repositorio_auth.dart';
import 'repositorio_proyectos.dart';
import 'sesion.dart';

// Grafo de dependencias de la capa de datos.
//
// Cada pieza se declara una vez y recibe las suyas por `ref`, igual que Spring
// hace con los constructores en el backend. Las pantallas piden lo que necesitan
// con `ref.read(...)` y nunca construyen nada a mano, asi que en un test basta
// con sustituir un proveedor (`overrides`) para darles un repositorio falso.
//
//   almacenDeTokens ─┬─▶ clienteApi ─┬─▶ repositorioAuth ──▶ sesion
//                    │               ├─▶ repositorioProyectos
//                    │               └─▶ repositorioAula
//                    └───────────────────▶ repositorioAuth

/// Almacen cifrado de tokens. Uno solo para toda la app: el cliente lo lee al
/// poner el `Bearer` y el repositorio de auth lo escribe al entrar y salir.
final almacenDeTokensProvider = Provider<AlmacenDeTokens>((ref) => AlmacenDeTokens());

/// Cliente HTTP compartido. Tiene que ser **uno**: la renovacion de token en
/// curso vive dentro, y dos clientes podrian renovar a la vez con el mismo
/// refresh, cosa que el servidor trata como un robo y cierra todas las sesiones.
final clienteApiProvider = Provider<ClienteApi>((ref) {
  final cliente = ClienteApi(almacen: ref.watch(almacenDeTokensProvider));
  // El interceptor no conoce la sesion; solo avisa. Se lee en el momento del
  // aviso, no al construir, porque la sesion depende a su vez de este cliente.
  cliente.alPerderLaSesion = () => ref.read(sesionProvider.notifier).sesionPerdida();
  return cliente;
});

final repositorioAuthProvider = Provider<RepositorioAuth>(
  (ref) => RepositorioAuth(
    cliente: ref.watch(clienteApiProvider),
    almacen: ref.watch(almacenDeTokensProvider),
  ),
);

final repositorioProyectosProvider = Provider<RepositorioProyectos>(
  (ref) => RepositorioProyectos(cliente: ref.watch(clienteApiProvider)),
);

/// Cursos, ranking y alumnado del centro.
final repositorioAulaProvider = Provider<RepositorioAula>(
  (ref) => RepositorioAula(cliente: ref.watch(clienteApiProvider)),
);
