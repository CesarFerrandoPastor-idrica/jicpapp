package com.jicp.api.ranking

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.contabilidad.CuentaRepository
import com.jicp.api.inversion.InversionRepository
import com.jicp.api.inversion.PrecioDeMercado
import com.jicp.api.seguridad.CentroDelUsuario
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.SinPermisoException
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode

/** Un alumno en la clasificacion de su centro. Todo lo calcula el servidor. */
data class EntradaDeRankingResponse(
    val posicion: Int,
    val idAlumno: Int,
    val nombre: String,
    val apellido: String,
    /** Dinero en la cartera, sin invertir. */
    val saldo: BigDecimal,
    /** Lo que valen hoy sus participaciones, a precio de mercado. */
    val valorParticipaciones: BigDecimal,
    /** Saldo + participaciones: todo lo que tiene. */
    val patrimonio: BigDecimal,
    /** Con lo que empezo (su concesion inicial). */
    val saldoInicial: BigDecimal,
    /** Cuanto ha ganado o perdido respecto a lo que recibio al empezar, en porcentaje. */
    val rentabilidad: BigDecimal,
)

@Service
@Transactional(readOnly = true)
class RankingService(
    private val alumnos: AlumnoRepository,
    private val cuentas: CuentaRepository,
    private val inversiones: InversionRepository,
) {

    /**
     * Clasificacion del alumnado de un centro.
     *
     * Ordena por **rentabilidad**, no por patrimonio: la concesion inicial puede no ser la
     * misma para todos (los alumnos dados de alta antes del saldo fijo empezaron con otra
     * cantidad), y comparar cantidades absolutas premiaria a quien empezo con mas, no a
     * quien mejor ha invertido. A igual rentabilidad, desempata el patrimonio.
     *
     * Las participaciones se valoran al precio de hoy, igual que en el portafolio. Las de
     * los proyectos que fundo cuentan tambien: son suyas y valen lo que el mercado diga.
     */
    fun deCentro(idColegio: Int): List<EntradaDeRankingResponse> {
        val filas = alumnos.findByColegioIdOrderByApellidoAscNombreAsc(idColegio).map { alumno ->
            val id = alumno.id!!
            val saldo = cuentas.carteraDe(id)?.saldo ?: BigDecimal.ZERO
            val valorParticipaciones = inversiones.findByAlumnoIdOrderByIdDesc(id)
                .map { PrecioDeMercado.importe(it.participaciones, PrecioDeMercado.precioActual(it.proyecto)) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
            val patrimonio = saldo.add(valorParticipaciones)

            EntradaDeRankingResponse(
                posicion = 0,
                idAlumno = id,
                nombre = alumno.nombre,
                apellido = alumno.apellido,
                saldo = saldo,
                valorParticipaciones = valorParticipaciones,
                patrimonio = patrimonio,
                saldoInicial = alumno.jicpInicial,
                rentabilidad = rentabilidad(patrimonio, alumno.jicpInicial),
            )
        }

        return filas
            .sortedWith(compareByDescending<EntradaDeRankingResponse> { it.rentabilidad }.thenByDescending { it.patrimonio })
            .mapIndexed { i, fila -> fila.copy(posicion = i + 1) }
    }

    /** (patrimonio - inicial) / inicial, en porcentaje con dos decimales. */
    private fun rentabilidad(patrimonio: BigDecimal, inicial: BigDecimal): BigDecimal =
        if (inicial.signum() == 0) {
            BigDecimal.ZERO.setScale(2)
        } else {
            patrimonio.subtract(inicial)
                .multiply(CIEN)
                .divide(inicial, 2, RoundingMode.HALF_UP)
        }

    private companion object {
        val CIEN = BigDecimal(100)
    }
}

@RestController
@RequestMapping("/api/v1/ranking")
class RankingController(
    private val servicio: RankingService,
    private val centros: CentroDelUsuario,
) {

    /**
     * Ranking del centro del profesor. El centro sale del token: un profesor no puede
     * pedir el de otro colegio.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('PROFESOR')")
    @GetMapping
    fun ranking(@AuthenticationPrincipal actor: UsuarioAutenticado): List<EntradaDeRankingResponse> {
        val colegio = centros.de(actor)
            ?: throw SinPermisoException("El ranking es de un centro y este usuario no pertenece a ninguno")
        return servicio.deCentro(colegio)
    }
}
