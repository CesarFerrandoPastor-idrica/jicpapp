package com.jicp.api.inversion

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.SinPermisoException
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class InversionController(
    private val servicio: InversionService,
    private val alumnos: AlumnoRepository,
) {

    /**
     * Comprar participaciones.
     *
     * El `Idempotency-Key` es obligatorio: sin el, un doble toque o el reintento de una
     * peticion cuya respuesta se perdio comprarian dos veces. El cliente genera un UUID
     * por **intento de compra** y lo reutiliza en todos los reintentos de ese intento.
     *
     * El try/catch vive aqui y no en el servicio a proposito: cuando el indice unico para
     * una peticion duplicada, PostgreSQL deja la transaccion abortada y no admite mas
     * consultas. Al estar el controlador fuera de la transaccion, la relectura ocurre en
     * una nueva y puede devolverse el recibo original.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ALUMNO')")
    @PostMapping("/inversiones")
    fun invertir(
        @Valid @RequestBody peticion: InvertirRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) claveIdempotencia: String?,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ResponseEntity<InversionResponse> {
        val clave = claveIdempotencia?.trim().orEmpty()
        if (clave.isEmpty()) {
            throw ConflictoException("Falta la cabecera Idempotency-Key")
        }
        if (clave.length > LONGITUD_MAXIMA_CLAVE) {
            throw ConflictoException("La Idempotency-Key no puede pasar de $LONGITUD_MAXIMA_CLAVE caracteres")
        }

        // El actor sale del token firmado, nunca del cuerpo de la peticion.
        val idAlumno = idAlumnoDe(actor)

        return try {
            ResponseEntity.ok(servicio.invertir(idAlumno, peticion, clave))
        } catch (e: DataIntegrityViolationException) {
            // Otra peticion con la misma clave gano la carrera. Se devuelve SU recibo con
            // 200, no un error: el cliente necesita el saldo para pintar la pantalla.
            servicio.recuperarPorClave(idAlumno, clave)
                ?.let { ResponseEntity.ok(it) }
                ?: throw e
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ALUMNO')")
    @GetMapping("/portafolio")
    fun portafolio(@AuthenticationPrincipal actor: UsuarioAutenticado): List<PosicionResponse> =
        servicio.portafolioDe(idAlumnoDe(actor))

    /** Estado del mercado de un proyecto. Abierto: es informacion publica del catalogo. */
    @GetMapping("/proyectos/{id}/mercado")
    fun mercado(@PathVariable id: Int): MercadoProyectoResponse = servicio.mercadoDe(id)

    @GetMapping("/proyectos/{id}/inversores")
    fun inversores(@PathVariable id: Int): List<InversorResponse> = servicio.inversoresDe(id)

    private fun idAlumnoDe(actor: UsuarioAutenticado): Int =
        alumnos.findByUsuarioId(actor.idUsuario)?.id
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")

    private companion object {
        /** Coincide con el ancho de `operacion_jicp.clave_idempotencia`. */
        const val LONGITUD_MAXIMA_CLAVE = 64
    }
}
