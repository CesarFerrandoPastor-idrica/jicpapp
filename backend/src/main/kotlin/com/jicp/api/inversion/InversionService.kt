package com.jicp.api.inversion

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.contabilidad.ContabilidadService
import com.jicp.api.contabilidad.CuentaRepository
import com.jicp.api.contabilidad.OperacionJicpRepository
import com.jicp.api.contabilidad.OrdenTransferencia
import com.jicp.api.contabilidad.TipoOperacion
import com.jicp.api.proyecto.AlumnoProyectoRepository
import com.jicp.api.proyecto.EstadosProyecto
import com.jicp.api.proyecto.Proyecto
import com.jicp.api.proyecto.ProyectoRepository
import com.jicp.api.proyecto.RolesProyecto
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.RecursoNoEncontradoException
import com.jicp.api.shared.SinPermisoException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional(readOnly = true)
class InversionService(
    private val proyectos: ProyectoRepository,
    private val alumnos: AlumnoRepository,
    private val miembros: AlumnoProyectoRepository,
    private val inversiones: InversionRepository,
    private val movimientos: MovimientoInversionRepository,
    private val contabilidad: ContabilidadService,
    private val cuentas: CuentaRepository,
    private val operaciones: OperacionJicpRepository,
) {

    /**
     * Compra participaciones de un proyecto.
     *
     * Todo ocurre en una sola transaccion: bloqueo del proyecto, validaciones, movimiento
     * de dinero, emision de participaciones y actualizacion de la posicion. Si el saldo no
     * llega, no se escribe nada.
     *
     * El actor llega como id, ya extraido del JWT por el controlador: aqui no se acepta
     * ningun identificador que venga del cuerpo de la peticion.
     */
    @Transactional
    fun invertir(
        idAlumnoActor: Int,
        peticion: InvertirRequest,
        claveIdempotencia: String,
    ): InversionResponse {
        val inversor = alumnos.findById(idAlumnoActor)
            .orElseThrow { RecursoNoEncontradoException("alumno", idAlumnoActor) }

        // Se bloquea el proyecto ANTES que las cuentas, y siempre en este orden, para que
        // dos compras simultaneas no lean las mismas participaciones emitidas.
        proyectos.bloquear(peticion.idProyecto)
            ?: throw RecursoNoEncontradoException("proyecto", peticion.idProyecto)

        // La clave se mira ANTES que cualquier validacion. Un reintento llega cuando la
        // compra original ya ha subido el precio (o agotado la ronda): validarlo como si
        // fuera nuevo contestaria 409 a una compra que SI se hizo, y el cliente la daria
        // por fallida. Va despues del bloqueo para que un duplicado simultaneo espere a
        // que el original confirme y lo encuentre aqui.
        reciboPrevio(inversor.id!!, claveIdempotencia)?.let { return it }

        val proyecto = proyectos.buscarConRelaciones(peticion.idProyecto)!!

        validarQueSePuedeInvertir(proyecto, inversor.id!!, inversor.colegio.id!!)

        val participaciones = peticion.participaciones
        val disponibles = PrecioDeMercado.disponibles(proyecto)
        if (participaciones > disponibles) {
            throw RondaAgotadaException(disponibles)
        }

        // El precio lo pone el servidor leyendo la base. Si el cliente dice haber visto
        // otro, se rechaza en lugar de cobrarle a un precio que no vio.
        val precioUnitario = PrecioDeMercado.precioActual(proyecto)
        peticion.precioUnitarioEsperado?.let { esperado ->
            if (esperado.compareTo(precioUnitario) != 0) {
                throw PrecioCambiadoException(esperado, precioUnitario)
            }
        }

        val importe = PrecioDeMercado.importe(participaciones, precioUnitario)
        if (importe <= BigDecimal.ZERO) {
            throw ConflictoException("El importe de la compra debe ser mayor que cero")
        }

        val cartera = cuentas.carteraDe(inversor.id!!)
            ?: throw IllegalStateException("El alumno ${inversor.id} no tiene cartera")
        val tesoreria = cuentas.tesoreriaDe(proyecto.id!!)
            ?: throw IllegalStateException("El proyecto ${proyecto.id} no tiene tesoreria")

        val resultado = contabilidad.ejecutar(
            OrdenTransferencia(
                tipo = TipoOperacion.INVERSION,
                actor = inversor,
                idCuentaOrigen = cartera.id!!,
                idCuentaDestino = tesoreria.id!!,
                importe = importe,
                claveIdempotencia = claveIdempotencia,
                proyecto = proyecto,
            ),
        )

        // Si la operacion ya existia (reintento), no se emite capital otra vez: se
        // devuelve el recibo original.
        movimientos.findByOperacionId(resultado.idOperacion)?.let {
            return recibo(it, resultado.saldoOrigen)
        }

        // Ultima red para la carrera que [reciboPrevio] no puede ver: otra operacion del
        // mismo alumno con esta clave confirmada justo entre aquella consulta y la de la
        // contabilidad. Lanzar aqui deshace la transaccion entera, emision incluida.
        if (operaciones.getReferenceById(resultado.idOperacion).tipo != TipoOperacion.INVERSION) {
            throw ConflictoException(
                "Esa Idempotency-Key ya se uso para otra operacion. Genera una nueva para cada compra",
            )
        }

        proyecto.participacionesEmitidas =
            proyecto.participacionesEmitidas.add(participaciones)

        val posicion = inversiones.findByProyectoIdAndAlumnoId(proyecto.id!!, inversor.id!!)
            ?: inversiones.save(Inversion(proyecto = proyecto, alumno = inversor))

        posicion.precioMedio = nuevoPrecioMedio(posicion, participaciones, precioUnitario)
        posicion.participaciones = posicion.participaciones.add(participaciones)

        val movimiento = movimientos.save(
            MovimientoInversion(
                inversion = posicion,
                operacion = operaciones.getReferenceById(resultado.idOperacion),
                tipo = TipoMovimiento.COMPRA,
                participaciones = participaciones,
                precioUnitario = precioUnitario,
                importe = importe,
            ),
        )

        return recibo(movimiento, resultado.saldoOrigen)
    }

    /**
     * Recupera el recibo de una compra ya ejecutada.
     *
     * Lo usa el controlador cuando dos peticiones con la misma clave chocan: la que pierde
     * la carrera devuelve 200 con el resultado de la que gano, no un error.
     */
    @Transactional(readOnly = true)
    fun recuperarPorClave(idAlumnoActor: Int, claveIdempotencia: String): InversionResponse? {
        val operacion = operaciones
            .findByActorIdAndClaveIdempotencia(idAlumnoActor, claveIdempotencia) ?: return null
        val movimiento = movimientos.findByOperacionId(operacion.id!!) ?: return null
        return recibo(movimiento, contabilidad.saldoDe(idAlumnoActor))
    }

    /**
     * El recibo de la compra que ya se hizo con esta clave, o null si la clave es nueva.
     *
     * Si la clave existe pero pertenece a otra cosa —la concesion inicial o el alta de un
     * proyecto, cuyas claves genera el servidor y son predecibles— se rechaza. Dejarla
     * pasar haria que la contabilidad reconociese la operacion antigua como "ya cobrada"
     * y la compra emitiria participaciones sin mover un solo JICP.
     */
    private fun reciboPrevio(idAlumnoActor: Int, claveIdempotencia: String): InversionResponse? {
        val operacion = operaciones
            .findByActorIdAndClaveIdempotencia(idAlumnoActor, claveIdempotencia) ?: return null
        val movimiento = movimientos.findByOperacionId(operacion.id!!)
            ?: throw ConflictoException(
                "Esa Idempotency-Key ya se uso para otra operacion. Genera una nueva para cada compra",
            )
        return recibo(movimiento, contabilidad.saldoDe(idAlumnoActor))
    }

    /** El portafolio del alumno: sus posiciones valoradas a precio de hoy. */
    fun portafolioDe(idAlumno: Int): List<PosicionResponse> =
        inversiones.findByAlumnoIdOrderByIdDesc(idAlumno).map { posicion ->
            val precioActual = PrecioDeMercado.precioActual(posicion.proyecto)
            val valorActual = PrecioDeMercado.importe(posicion.participaciones, precioActual)
            val coste = PrecioDeMercado.importe(posicion.participaciones, posicion.precioMedio)

            PosicionResponse(
                idProyecto = posicion.proyecto.id!!,
                nombreProyecto = posicion.proyecto.nombre,
                categoria = posicion.proyecto.categoria.nombre,
                estado = posicion.proyecto.estado.nombre,
                participaciones = posicion.participaciones,
                precioMedio = posicion.precioMedio,
                precioActual = precioActual,
                valorActual = valorActual,
                plusvalia = valorActual.subtract(coste),
            )
        }

    /** Estado de mercado de un proyecto: lo que la ficha necesita para dejar invertir. */
    fun mercadoDe(idProyecto: Int): MercadoProyectoResponse {
        val proyecto = proyectos.buscarConRelaciones(idProyecto)
            ?: throw RecursoNoEncontradoException("proyecto", idProyecto)

        return MercadoProyectoResponse(
            idProyecto = idProyecto,
            precioBase = proyecto.precioBase,
            precioActual = PrecioDeMercado.precioActual(proyecto),
            participacionesTotales = proyecto.participacionesTotales,
            participacionesEmitidas = proyecto.participacionesEmitidas,
            participacionesDisponibles = PrecioDeMercado.disponibles(proyecto),
            recaudado = cuentas.tesoreriaDe(idProyecto)?.saldo ?: BigDecimal.ZERO,
            inversores = inversiones.findByProyectoIdOrderByParticipacionesDesc(idProyecto).size,
        )
    }

    fun inversoresDe(idProyecto: Int): List<InversorResponse> =
        inversiones.findByProyectoIdOrderByParticipacionesDesc(idProyecto).map {
            InversorResponse(
                idAlumno = it.alumno.id!!,
                nombre = it.alumno.nombre,
                apellido = it.alumno.apellido,
                participaciones = it.participaciones,
            )
        }

    // --- validaciones ---

    private fun validarQueSePuedeInvertir(proyecto: Proyecto, idAlumno: Int, idColegio: Int) {
        if (proyecto.estado.id != EstadosProyecto.PUBLICADO) {
            throw ConflictoException(
                "Solo se puede invertir en proyectos publicados; este esta en estado " +
                    "'${proyecto.estado.nombre}'",
            )
        }

        // Mismo centro: la bolsa es de aula, no un mercado abierto entre colegios.
        if (proyecto.colegio.id != idColegio) {
            throw SinPermisoException("Solo puedes invertir en proyectos de tu propio centro")
        }

        // No se invierte en el proyecto de uno mismo: si no, seria una forma de inflar
        // las propias metricas moviendo dinero de un bolsillo al otro.
        val vinculo = miembros.findByProyectoIdAndAlumnoId(proyecto.id!!, idAlumno)
        if (vinculo != null && vinculo.rol.id == RolesProyecto.CREADOR) {
            throw ConflictoException("No puedes invertir en tu propio proyecto")
        }
    }

    /**
     * Media ponderada entre lo que ya tenia y lo que acaba de comprar. Con precio
     * variable es lo unico que permite saber despues si la posicion gana o pierde.
     */
    private fun nuevoPrecioMedio(
        posicion: Inversion,
        participacionesNuevas: BigDecimal,
        precioUnitario: BigDecimal,
    ): BigDecimal {
        val totalPrevio = posicion.participaciones.multiply(posicion.precioMedio)
        val totalNuevo = participacionesNuevas.multiply(precioUnitario)
        val participacionesFinales = posicion.participaciones.add(participacionesNuevas)

        return totalPrevio.add(totalNuevo)
            .divide(participacionesFinales, PrecioDeMercado.ESCALA_PRECIO, RoundingMode.HALF_UP)
    }

    private fun recibo(
        movimiento: MovimientoInversion,
        saldoCartera: BigDecimal,
    ): InversionResponse {
        val posicion = movimiento.inversion
        val proyecto = posicion.proyecto
        return InversionResponse(
            idOperacion = movimiento.operacion.id!!,
            idProyecto = proyecto.id!!,
            nombreProyecto = proyecto.nombre,
            participacionesCompradas = movimiento.participaciones,
            precioUnitario = movimiento.precioUnitario,
            importe = movimiento.importe,
            participacionesTotales = posicion.participaciones,
            precioMedio = posicion.precioMedio,
            precioSiguiente = PrecioDeMercado.precioActual(proyecto),
            saldoCartera = saldoCartera,
            fecha = movimiento.fechaRegistro,
        )
    }
}

/** No quedan participaciones bastantes en la ronda. Se traduce a 409. */
class RondaAgotadaException(disponibles: BigDecimal) :
    ConflictoException(
        if (disponibles <= BigDecimal.ZERO) {
            "La ronda de este proyecto esta completa: no quedan participaciones"
        } else {
            "Solo quedan $disponibles participaciones disponibles"
        },
    )

/** El precio subio entre que se pinto la pantalla y se pulso comprar. Se traduce a 409. */
class PrecioCambiadoException(esperado: BigDecimal, actual: BigDecimal) :
    ConflictoException(
        "El precio ha cambiado: viste $esperado JICP y ahora es $actual. Revisa y confirma de nuevo",
    )
