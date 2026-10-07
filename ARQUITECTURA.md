# Arquitectura de JICP

El proyecto tiene **tres piezas**, y cada una solo habla con la de al lado:

```
App Flutter  ──HTTPS + JWT──▶  API (Kotlin + Spring Boot)  ──JPA──▶  PostgreSQL
 (lib/)                         (backend/)                           (docker-compose)
 pide y muestra                 decide y valida                      guarda y garantiza
```

La regla que lo explica todo: **la app nunca decide nada que importe**. No calcula saldos, no cobra y no elige el rol. Manda *intenciones* ("quiero comprar 2 participaciones") y pinta lo que el servidor le contesta. Por eso casi toda la lógica está en el backend.

---

## 1. La app Flutter (`lib/`)

Está organizada en capas. Cada capa solo llama a la de debajo:

```
screens/          Pantallas: pintan y reaccionan al usuario
   │              (piden sus dependencias con `ref`, nunca las construyen)
   │
api/sesion.dart   Quién ha entrado, su rol y el último saldo conocido
   │
api/repositorio_*.dart   Una función por llamada a la API (login, mercado, invertir…)
   │
api/cliente_api.dart     dio: pone el token, lo renueva y traduce los errores
   │
   ▼  HTTP
```

### Inyección de dependencias con Riverpod

Igual que en el backend Spring crea los servicios y se los pasa a los controladores, en la app lo hace **Riverpod**. Todo el grafo está en [proveedores.dart](lib/api/proveedores.dart):

```
almacenDeTokensProvider ─┬─▶ clienteApiProvider ─┬─▶ repositorioAuthProvider ──▶ sesionProvider
                         │                       └─▶ repositorioProyectosProvider
                         └───────────────────────────▶ repositorioAuthProvider
```

- `main.dart` envuelve la app en un `ProviderScope`, que es quien guarda las instancias.
- Las pantallas son `ConsumerStatefulWidget` y piden lo que necesitan:
  - `ref.read(repositorioProyectosProvider)` para llamar a la API;
  - `ref.watch(sesionProvider)` para leer la sesión y repintarse cuando cambie;
  - `ref.read(sesionProvider.notifier).salir()` para actuar sobre ella.
- En los tests se cambia cualquier pieza sin tocar la pantalla:
  `ProviderScope(overrides: [repositorioProyectosProvider.overrideWithValue(falso)])`.
  Así se prueba, por ejemplo, que la hoja de compra reintenta con la misma `Idempotency-Key` cuando no llega respuesta.
- Un detalle de Riverpod 3: tras un `await`, `ref` no se puede usar si la pantalla se ha cerrado. Por eso, cuando hace falta algo después de esperar, se guarda antes (`final sesion = ref.read(sesionProvider.notifier);`).

- **[screens/](lib/screens/)**: una pantalla por archivo. Las conectadas a la API son `login_screen`, `market_screen`, `ficha_proyecto_screen`, `create_project_screen` y `portfolio_screen`. Todas siguen el mismo patrón:
  - un `_cargar()` que pide datos;
  - tres estados: cargando, error y datos;
  - recarga al deslizar hacia abajo.

  Las demás todavía leen de [mock_data.dart](lib/mock_data.dart).
- **[sesion.dart](lib/api/sesion.dart)**: un `Notifier` de Riverpod con el estado de la sesión (`EstadoDeSesion`): el perfil (`/yo`), el último saldo y si la sesión ha caducado. `main.dart` escucha esa caducidad para volver a la pantalla de acceso.
- **[repositorio_auth.dart](lib/api/repositorio_auth.dart)** y **[repositorio_proyectos.dart](lib/api/repositorio_proyectos.dart)**: cada método hace una petición, comprueba el código de respuesta y convierte el JSON en un modelo. Si algo falla, lanza un `ErrorApi` con el mensaje del servidor.
- **[modelos_*.dart](lib/api/modelos_proyecto.dart)**: clases inmutables con un `desdeJson`, que reflejan lo que devuelve la API (`ProyectoDeMercado`, `ReciboDeInversion`, `Posicion`…).
- **[cliente_api.dart](lib/api/cliente_api.dart)**: la pieza más técnica. Hace tres cosas:
  1. Añade `Authorization: Bearer <token>` a cada petición.
  2. Si recibe un `401`, renueva el token **una sola vez** y repite la petición.
  3. Convierte los errores del servidor (formato RFC 7807) en un mensaje que se puede enseñar.
- **[almacen_de_tokens.dart](lib/api/almacen_de_tokens.dart)**: guarda los tokens en el almacén cifrado del sistema. Solo tokens, nunca saldos.

---

## 2. El backend (`backend/src/main/kotlin/com/jicp/api/`)

### Organizado por concepto, no por capa técnica

En vez de tener una carpeta `controllers/` y otra `services/`, cada concepto del dominio tiene **su carpeta con sus cinco piezas**:

```
alumno/
├── Alumno.kt              Entidad: la tabla `alumno` vista desde Kotlin
├── AlumnoRepository.kt    Consultas a la base (Spring Data JPA)
├── AlumnoService.kt       Reglas de negocio, dentro de @Transactional
├── AlumnoController.kt    Endpoints HTTP: recibe JSON y devuelve JSON
└── AlumnoDtos.kt          Formas del JSON de entrada y salida (Request/Response)
```

Los módulos:

| Carpeta | Qué hace |
|---|---|
| `colegio/`, `alumno/`, `profesor/` | Centros y personas |
| `proyecto/` | Proyectos, equipo (creador/socio/colaborador), catálogos y comentarios |
| `seguridad/` | Usuarios, login, JWT, renovación de tokens y quién puede entrar a qué |
| `contabilidad/` | **El dinero.** Cuentas, operaciones y apuntes. Es el núcleo crítico |
| `inversion/` | Comprar participaciones, el precio de mercado y el portafolio |
| `shared/` | Excepciones y su traducción a respuestas de error |
| `config/` | BCrypt, CORS y Swagger |

### Recorrido de una petición

```
Petición HTTP
  │
  ▼  seguridad/FiltroJwt.kt          lee el token y sabe QUIÉN eres (id de usuario y rol)
  ▼  seguridad/SeguridadConfig.kt    ¿esta ruta exige token?  → si no lo traes, 401
  ▼  XxxController.kt                @PreAuthorize("hasRole('ALUMNO')") → si no lo eres, 403
  │                                  saca tu id de alumno DEL TOKEN, nunca del JSON
  ▼  XxxService.kt                   @Transactional: valida las reglas y hace el trabajo
  ▼  XxxRepository.kt                consultas a PostgreSQL
  │
  ✗ si algo falla → lanza ConflictoException, SinPermisoException…
                    → shared/ManejadorDeErrores.kt la convierte en 409/403/404 con un "detail"
```

Ese `detail` es el texto que luego enseña la app. Por eso los mensajes de error de la app están en español y son concretos: los escribe el servidor.

### La regla de oro del dinero

**Solo [ContabilidadService.kt](backend/src/main/kotlin/com/jicp/api/contabilidad/ContabilidadService.kt) toca saldos.** Los demás módulos le piden operaciones ("pasa 330 JICP de la cartera de Diego a la tesorería del proyecto 3"), pero ninguno escribe un saldo por su cuenta. Así las garantías se cumplen en un solo sitio.

---

## 3. La base de datos

- **El esquema lo crean las migraciones** de [db/migration/](backend/src/main/resources/db/migration/), de `V1` a `V6`. Flyway las aplica en orden al arrancar. Una migración que ya se aplicó no se edita nunca: los cambios van en una `V7` nueva.
- Hibernate arranca en modo `validate`: si una entidad Kotlin no coincide con su tabla, la API **no arranca**. Así no se corrompen datos en silencio.
- Las tablas, por grupos:
  - **Identidad**: `usuario` (email, contraseña y rol) → de ella cuelgan `alumno` y `profesor`. También `refresh_token`.
  - **Proyectos**: `proyecto`, `alumno_proyecto` (quién está y con qué rol) y los catálogos.
  - **Dinero**:
    - `cuenta`: una por alumno (cartera), una por proyecto (tesorería) y una del sistema (emisión).
    - `operacion_jicp`: cada operación, con su clave de idempotencia.
    - `apunte_jicp`: los movimientos de cada operación. Nunca se borran ni se editan.
  - **Mercado**: `inversion` (tu posición en un proyecto), `movimiento_inversion` (cada compra) y `comentario_proyecto`.
- La base hace de **última red de seguridad**. Por ejemplo, `CHECK (saldo >= 0)` impide un saldo negativo aunque el código Kotlin tuviera un fallo.

---

## 4. Ejemplo completo: Diego compra 2 participaciones

1. **App, [ficha_proyecto_screen.dart](lib/screens/ficha_proyecto_screen.dart)**: Diego escribe `2` en la hoja de compra. La hoja ya ha generado una `Idempotency-Key` (un UUID) y llama a `repositorio.invertir(...)`.
2. **App, [repositorio_proyectos.dart](lib/api/repositorio_proyectos.dart)**: envía
   `POST /inversiones` con `{idProyecto: 3, participaciones: 2, precioUnitarioEsperado: 165}` y la cabecera `Idempotency-Key`. No manda ni el importe ni el saldo.
3. **API, [InversionController.kt](backend/src/main/kotlin/com/jicp/api/inversion/InversionController.kt)**: comprueba que eres alumno, que hay clave y saca el id de Diego del token.
4. **API, [InversionService.kt](backend/src/main/kotlin/com/jicp/api/inversion/InversionService.kt)**, todo dentro de una sola transacción:
   1. **Bloquea el proyecto** (`SELECT … FOR UPDATE`) para que dos compras a la vez no lean el mismo precio.
   2. **¿Esta clave ya se usó?** Si es así, devuelve el recibo de entonces y termina.
   3. Valida: proyecto publicado, mismo centro, que no sea tuyo, que queden participaciones y que el precio coincida.
   4. Calcula el precio con [PrecioDeMercado.kt](backend/src/main/kotlin/com/jicp/api/inversion/PrecioDeMercado.kt): `base × (1 + emitidas / totales)`.
   5. Pide a **ContabilidadService** que mueva el dinero. Esta bloquea las dos cuentas por id ascendente, comprueba el saldo, resta en una, suma en la otra y escribe dos apuntes que **suman cero** (doble partida).
   6. Suma las participaciones emitidas, actualiza la posición de Diego (con su precio medio) y guarda el movimiento.
5. **Respuesta**: un recibo con `saldoCartera: 4670`. Si algo falló a mitad, la transacción se deshace entera y no queda nada a medias.
6. **App**: pinta **ese** saldo (`sesion.anotarSaldoDelServidor`), enseña el recibo y vuelve a leer la ficha.

---

## 5. Conceptos que aparecen por todo el código

| Concepto | Qué es | Dónde mirar |
|---|---|---|
| **Doble partida** | Cada operación son dos apuntes que suman 0. Si alguna vez `SUM(importe) ≠ 0`, se ha creado o destruido dinero | `ContabilidadService.ejecutar` |
| **Cuenta de emisión** | De donde sale el saldo inicial de cada alumno. Su saldo negativo es todo el dinero en circulación | `concederSaldoInicial` |
| **Bloqueo pesimista** | `FOR UPDATE`: la segunda compra espera a que termine la primera. Se bloquea siempre en el mismo orden para evitar que dos compras se queden esperándose mutuamente | `CuentaRepository.bloquear`, `ProyectoRepository.bloquear` |
| **Idempotencia** | Misma clave = misma compra. Un reintento devuelve el recibo original y no cobra dos veces | `reciboPrevio`, `idempotencia.dart` |
| **El actor sale del token** | Nunca se acepta un `idAlumno` en el JSON. Si no, cualquiera podría gastar el saldo de otro | Todos los controladores |
| **Tesorería del proyecto** | Lo invertido va al proyecto, no al bolsillo del creador | `abrirTesoreria` |

---

## 6. Si quieres cambiar algo, dónde ir

- **Un endpoint nuevo**: `XxxController` → `XxxService` → `XxxDtos`, y un test en `backend/src/test/`.
- **Una columna o tabla**: una migración `V7__….sql` nueva, y después la entidad Kotlin.
- **Una pantalla nueva contra la API**: un método en el repositorio de `lib/api/`, su modelo con `desdeJson`, y una pantalla `ConsumerStatefulWidget` con el patrón `_cargar()` del Portafolio o el Mercado, pidiendo el repositorio con `ref.read(...)`.
- **Una dependencia nueva** (otro repositorio, por ejemplo): un `Provider` más en [proveedores.dart](lib/api/proveedores.dart).
- **Las reglas exactas que aplica el servidor**: los tests de [MercadoTest.kt](backend/src/test/kotlin/com/jicp/api/MercadoTest.kt) y [AutenticacionTest.kt](backend/src/test/kotlin/com/jicp/api/AutenticacionTest.kt). Sus nombres son frases ("un alumno no puede invertir en su propio proyecto") y sirven de especificación.

Una forma práctica de ver todo esto en marcha: arranca el backend, abre `http://localhost:8080/swagger-ui.html` y prueba los endpoints a mano mientras lees el controlador correspondiente.
