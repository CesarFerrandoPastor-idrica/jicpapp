import 'dart:math';

/// Genera la `Idempotency-Key` de una operación con moneda.
///
/// Es un UUID v4 hecho a mano en lugar de traer un paquete para ello: son seis
/// líneas y la única exigencia real es que salga de un generador criptográfico,
/// para que dos alumnos no puedan coincidir ni adivinar la clave de otro.
///
/// **Una clave por intento, no por petición.** La pantalla la genera cuando el
/// alumno decide comprar y la reutiliza en todos los reintentos de ese intento.
/// Si la respuesta se pierde por el camino, reintentar con la misma clave hace
/// que el servidor devuelva el recibo original en vez de cobrar dos veces.
String nuevaClaveDeIdempotencia() {
  final aleatorio = Random.secure();
  final bytes = List<int>.generate(16, (_) => aleatorio.nextInt(256));

  // Versión 4 y variante RFC 4122.
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}
