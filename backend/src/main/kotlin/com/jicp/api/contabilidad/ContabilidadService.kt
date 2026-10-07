package com.jicp.api.contabilidad

import com.jicp.api.alumno.Alumno
import com.jicp.api.proyecto.Proyecto
import com.jicp.api.shared.ConflictoException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Orden de mover dinero entre dos cuentas. El importe es siempre positivo: la
 * direccion la marcan origen y destino, no el signo.
 */
data class OrdenTransferencia(
    val tipo: TipoOperacion,
    val actor: Alumno,
    val idCuentaOrigen: Int,
    val idCuentaDestino: Int,
    val importe: BigDecimal,
    val claveIdempotencia: String,
    val proyecto: Proyecto? = null,
)

data class ResultadoOperacion(
    val idOperacion: Int,
    val saldoOrigen: BigDecimal,
    val saldoDestino: BigDecimal,
)

/**
 * El **unico** componente que escribe en `cuenta` y en `apunte_jicp`.
 *
 * El resto de modulos le piden operaciones semanticas; ninguno modifica un saldo por su
 * cuenta. Concentrarlo aqui es lo que permite garantizar de verdad las tres invariantes:
 * doble partida, saldo no negativo e idempotencia.
 *
 * **Sobre la idempotencia:** este servicio hace la comprobacion optimista y deja que el
 * indice unico pare las carreras. Recuperar el resultado original tras un choque NO se
 * hace aqui: en PostgreSQL, una transaccion que ha violado una restriccion queda abortada
 * y no admite mas consultas, asi que la relectura tiene que ocurrir en una transaccion
 * nueva. De eso se encarga el controlador, que esta fuera de la transaccion.
 */
@Service
class ContabilidadService(
    private val cuentas: CuentaRepository,
    private val operaciones: OperacionJicpRepository,
    private val apuntes: ApunteJicpRepository,
) {

    /** Abre la cartera de un alumno. Sin saldo: el dinero entra con [concederSaldoInicial]. */
    @Transactional
    fun abrirCartera(alumno: Alumno): Cuenta =
        cuentas.save(Cuenta(tipo = TipoCuenta.CARTERA_ALUMNO, alumno = alumno))

    /** Abre la tesoreria de un proyecto. */
    @Transactional
    fun abrirTesoreria(proyecto: Proyecto): Cuenta =
        cuentas.save(Cuenta(tipo = TipoCuenta.TESORERIA_PROYECTO, proyecto = proyecto))

    /**
     * Crea dinero y lo abona en la cartera del alumno.
     *
     * La contrapartida es la cuenta de emision, la unica que puede quedarse en negativo:
     * su saldo negativo es la masa monetaria en circulacion. Sin ella el dinero apareceria
     * de la nada y la reconciliacion dejaria de cuadrar a cero.
     */
    @Transactional
    fun concederSaldoInicial(alumno: Alumno, importe: BigDecimal): ResultadoOperacion? {
        if (importe <= BigDecimal.ZERO) return null

        val emision = cuentas.emision()
            ?: throw IllegalStateException("No existe la cuenta de emision del sistema")
        val cartera = cuentas.carteraDe(requireNotNull(alumno.id))
            ?: throw IllegalStateException("El alumno ${alumno.id} no tiene cartera")

        return ejecutar(
            OrdenTransferencia(
                tipo = TipoOperacion.CONCESION_INICIAL,
                actor = alumno,
                idCuentaOrigen = requireNotNull(emision.id),
                idCuentaDestino = requireNotNull(cartera.id),
                importe = importe,
                claveIdempotencia = "concesion-inicial-${alumno.id}",
            ),
        )
    }

    /**
     * Mueve dinero de una cuenta a otra dejando sus apuntes.
     *
     * Se une a la transaccion de quien llama, para que descontar el dinero y crear el
     * proyecto (o registrar la posicion) sean atomicos: o pasa todo, o no pasa nada.
     */
    @Transactional
    fun ejecutar(orden: OrdenTransferencia): ResultadoOperacion {
        require(orden.importe > BigDecimal.ZERO) { "El importe debe ser positivo" }
        require(orden.idCuentaOrigen != orden.idCuentaDestino) {
            "El origen y el destino no pueden ser la misma cuenta"
        }

        // Atajo optimista: si la operacion ya se ejecuto, se devuelve su resultado y no
        // un error, porque el cliente necesita el saldo para pintar la pantalla. Entre
        // esta consulta y el INSERT cabe otra peticion identica; a esa la para el indice.
        operaciones.findByActorIdAndClaveIdempotencia(
            requireNotNull(orden.actor.id),
            orden.claveIdempotencia,
        )?.let { return resultadoDe(it) }

        // Bloqueo SIEMPRE en orden ascendente de id: dos transferencias cruzadas no pueden
        // quedarse esperandose mutuamente. El bloqueo cubre lectura y escritura dentro de
        // la misma transaccion; leer el saldo fuera y validar despues no serviria de nada.
        val ids = listOf(orden.idCuentaOrigen, orden.idCuentaDestino).sorted()
        val bloqueadas = cuentas.bloquear(ids)
        if (bloqueadas.size != 2) {
            throw IllegalStateException("No se han podido bloquear las cuentas $ids")
        }

        val origen = bloqueadas.first { it.id == orden.idCuentaOrigen }
        val destino = bloqueadas.first { it.id == orden.idCuentaDestino }

        // La validacion de negocio vive aqui, no en el cliente. El CHECK de la base es la
        // red de seguridad por si esta comprobacion se cayera algun dia.
        if (origen.tipo != TipoCuenta.EMISION_SISTEMA && origen.saldo < orden.importe) {
            throw SaldoInsuficienteException(origen.saldo, orden.importe)
        }

        origen.saldo = origen.saldo.subtract(orden.importe)
        destino.saldo = destino.saldo.add(orden.importe)

        val operacion = operaciones.save(
            OperacionJicp(
                tipo = orden.tipo,
                actor = orden.actor,
                proyecto = orden.proyecto,
                claveIdempotencia = orden.claveIdempotencia,
            ),
        )

        // Doble partida: los dos apuntes suman exactamente cero.
        apuntes.saveAll(
            listOf(
                ApunteJicp(operacion, origen, orden.importe.negate(), origen.saldo),
                ApunteJicp(operacion, destino, orden.importe, destino.saldo),
            ),
        )

        // Fuerza el INSERT ahora: asi un choque de idempotencia salta aqui y no al cerrar
        // la transaccion, donde ya no habria forma de distinguirlo de otro fallo.
        operaciones.flush()

        return ResultadoOperacion(
            idOperacion = requireNotNull(operacion.id),
            saldoOrigen = origen.saldo,
            saldoDestino = destino.saldo,
        )
    }

    /** Reconstruye el resultado de una operacion ya escrita, leyendo sus apuntes. */
    @Transactional(readOnly = true)
    fun resultadoDe(operacion: OperacionJicp): ResultadoOperacion {
        val suyos = apuntes.findByOperacionIdOrderByIdAsc(requireNotNull(operacion.id))
        return ResultadoOperacion(
            idOperacion = requireNotNull(operacion.id),
            saldoOrigen = suyos.firstOrNull { it.importe < BigDecimal.ZERO }?.saldoPosterior
                ?: BigDecimal.ZERO,
            saldoDestino = suyos.firstOrNull { it.importe > BigDecimal.ZERO }?.saldoPosterior
                ?: BigDecimal.ZERO,
        )
    }

    /** Saldo actual de la cartera de un alumno. Solo lectura: nadie escribe un saldo. */
    @Transactional(readOnly = true)
    fun saldoDe(idAlumno: Int): BigDecimal =
        cuentas.carteraDe(idAlumno)?.saldo ?: BigDecimal.ZERO
}

/** Se traduce a 409: el alumno no tiene bastante para lo que pide. */
class SaldoInsuficienteException(saldo: BigDecimal, importe: BigDecimal) :
    ConflictoException("Saldo insuficiente: tienes $saldo JICP y la operacion requiere $importe")
