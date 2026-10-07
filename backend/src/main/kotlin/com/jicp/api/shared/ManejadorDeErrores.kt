package com.jicp.api.shared

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Traduce las excepciones a respuestas RFC 7807 (application/problem+json).
 *
 * Hereda de [ResponseEntityExceptionHandler] para que las excepciones propias de Spring MVC
 * (JSON malformado, metodo no permitido, path variable con tipo incorrecto...) conserven su
 * codigo correcto: sin esa herencia, el handler generico de [Exception] las convertiria en 500.
 *
 * Ningun detalle interno (SQL, stack traces, nombres de constraint) sale al cliente:
 * se registra en el log del servidor y fuera se devuelve un texto neutro.
 */
@RestControllerAdvice
class ManejadorDeErrores : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(RecursoNoEncontradoException::class)
    fun noEncontrado(ex: RecursoNoEncontradoException): ProblemDetail =
        problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.message ?: "", "recurso-no-encontrado")

    @ExceptionHandler(ConflictoException::class)
    fun conflicto(ex: ConflictoException): ProblemDetail =
        problema(HttpStatus.CONFLICT, "Conflicto", ex.message ?: "", "conflicto")

    @ExceptionHandler(CredencialesInvalidasException::class)
    fun credenciales(ex: CredencialesInvalidasException): ProblemDetail =
        problema(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", ex.message ?: "", "credenciales-invalidas")

    /**
     * Rechazo de @PreAuthorize. Necesita handler propio: al lanzarse en el interceptor de
     * seguridad de metodo, la excepcion llega hasta este advice, y sin esta entrada la
     * recogeria el catch-all de [Exception] y saldria un 500 en vez de un 403.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun accesoDenegado(ex: AccessDeniedException): ProblemDetail =
        problema(
            HttpStatus.FORBIDDEN,
            "Acceso denegado",
            "Tu rol no permite realizar esta operacion",
            "acceso-denegado",
        )

    @ExceptionHandler(SinPermisoException::class)
    fun sinPermiso(ex: SinPermisoException): ProblemDetail =
        problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.message ?: "", "acceso-denegado")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integridad(ex: DataIntegrityViolationException): ProblemDetail {
        log.warn("Violacion de integridad en base de datos", ex)
        return problema(
            HttpStatus.CONFLICT,
            "Conflicto",
            "La operacion viola una restriccion de integridad de los datos",
            "integridad",
        )
    }

    @ExceptionHandler(Exception::class)
    fun inesperado(ex: Exception): ProblemDetail {
        log.error("Error no controlado", ex)
        return problema(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Error interno",
            "Se ha producido un error inesperado",
            "error-interno",
        )
    }

    /** Errores de validacion de los DTOs: 422 con el detalle campo a campo. */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val detalle = problema(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Datos invalidos",
            "Alguno de los campos enviados no es valido",
            "validacion",
        )
        detalle.setProperty(
            "errores",
            ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "valor invalido") },
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(detalle)
    }

    private fun problema(status: HttpStatus, titulo: String, detalle: String, tipo: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detalle).apply {
            title = titulo
            type = URI.create("https://jicp.app/errores/$tipo")
        }
}
