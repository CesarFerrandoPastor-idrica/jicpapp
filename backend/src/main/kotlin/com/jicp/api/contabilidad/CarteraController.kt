package com.jicp.api.contabilidad

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.SinPermisoException
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime

data class CarteraResponse(
    val idAlumno: Int,
    val saldo: BigDecimal,
)

/** Un apunte del libro tal y como lo ve el alumno en su historial de movimientos. */
data class MovimientoCarteraResponse(
    val idApunte: Long,
    val idOperacion: Int,
    val tipo: TipoOperacion,
    /** Negativo si sale dinero de la cartera, positivo si entra. */
    val importe: BigDecimal,
    val saldoPosterior: BigDecimal,
    val idProyecto: Int?,
    val nombreProyecto: String?,
    val fecha: LocalDateTime,
)

@Service
class CarteraService(
    private val cuentas: CuentaRepository,
    private val apuntes: ApunteJicpRepository,
) {

    @Transactional(readOnly = true)
    fun cartera(idAlumno: Int): CarteraResponse =
        CarteraResponse(
            idAlumno = idAlumno,
            saldo = cuentas.carteraDe(idAlumno)?.saldo ?: BigDecimal.ZERO,
        )

    @Transactional(readOnly = true)
    fun movimientos(idAlumno: Int, pagina: Int, tamano: Int): Page<MovimientoCarteraResponse> {
        val cuenta = cuentas.carteraDe(idAlumno) ?: return Page.empty()

        return apuntes
            .findByCuentaIdOrderByIdDesc(cuenta.id!!, PageRequest.of(pagina, tamano))
            .map { apunte ->
                MovimientoCarteraResponse(
                    idApunte = apunte.id!!,
                    idOperacion = apunte.operacion.id!!,
                    tipo = apunte.operacion.tipo,
                    importe = apunte.importe,
                    saldoPosterior = apunte.saldoPosterior,
                    idProyecto = apunte.operacion.proyecto?.id,
                    nombreProyecto = apunte.operacion.proyecto?.nombre,
                    fecha = apunte.fechaRegistro,
                )
            }
    }
}

/**
 * La cartera es **solo de lectura**. No existe `PUT /cartera` ni ningun endpoint que
 * acepte un saldo, y es una ausencia deliberada: el saldo se mueve pidiendo operaciones
 * (invertir, crear un proyecto), nunca asignandolo.
 */
@RestController
@RequestMapping("/api/v1/cartera")
class CarteraController(
    private val servicio: CarteraService,
    private val alumnos: AlumnoRepository,
) {

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ALUMNO')")
    @GetMapping
    fun cartera(@AuthenticationPrincipal actor: UsuarioAutenticado): CarteraResponse =
        servicio.cartera(idAlumnoDe(actor))

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ALUMNO')")
    @GetMapping("/movimientos")
    fun movimientos(
        @AuthenticationPrincipal actor: UsuarioAutenticado,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<MovimientoCarteraResponse> =
        servicio.movimientos(idAlumnoDe(actor), page, size.coerceIn(1, 100))

    private fun idAlumnoDe(actor: UsuarioAutenticado): Int =
        alumnos.findByUsuarioId(actor.idUsuario)?.id
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")
}
