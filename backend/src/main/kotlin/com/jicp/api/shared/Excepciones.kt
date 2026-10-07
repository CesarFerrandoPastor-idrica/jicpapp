package com.jicp.api.shared

/** El recurso solicitado no existe. Se traduce a 404. */
class RecursoNoEncontradoException(recurso: String, id: Any) :
    RuntimeException("No existe $recurso con id $id")

/**
 * La operacion choca con el estado actual de los datos (duplicados, reglas de negocio).
 * Se traduce a 409.
 *
 * Es abierta para que los conflictos con nombre propio —saldo insuficiente, precio
 * cambiado, ronda agotada— hereden de ella y compartan el mismo codigo sin repetir
 * un handler por cada uno.
 */
open class ConflictoException(mensaje: String) : RuntimeException(mensaje)

/**
 * Email desconocido, contrasena incorrecta, cuenta desactivada o refresh token invalido.
 * Se traduce a 401 con un mensaje unico: decir cual de las cuatro fue le confirmaria a
 * quien prueba credenciales que ha acertado una parte.
 */
class CredencialesInvalidasException :
    RuntimeException("Las credenciales no son validas")

/**
 * El usuario esta autenticado, pero el recurso no es suyo ni de su centro.
 * Se traduce a 403. Distinto de [CredencialesInvalidasException], que es un 401:
 * aqui sabemos quien eres, y precisamente por eso te decimos que no.
 */
class SinPermisoException(mensaje: String) : RuntimeException(mensaje)
