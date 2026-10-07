package com.jicp.api.inversion

import com.jicp.api.proyecto.Proyecto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cuanto cuesta una participacion ahora mismo.
 *
 * ```
 * precio = precio_base * (1 + emitidas / totales)
 * ```
 *
 * Lineal y acotada: con la ronda vacia se paga el precio base, y cuando esta llena se
 * paga el doble. Se eligio frente a una curva compuesta porque un alumno puede calcularla
 * a mano —que es justo lo que se quiere que entienda— y porque no se dispara sola.
 *
 * El precio se calcula **una vez por operacion** sobre el estado previo, no participacion
 * a participacion: comprar 5 de golpe paga las 5 al mismo precio. Es un escalon, no una
 * integral, y eso hace que el recibo sea comprobable con una multiplicacion.
 *
 * Vive en el servidor y solo en el servidor: validar el saldo no serviria de nada si el
 * cliente pudiera enviar `precioUnitario: 0.01`.
 */
object PrecioDeMercado {

    /** Decimales intermedios: se redondea solo al final, para no arrastrar sesgo. */
    private const val ESCALA_INTERMEDIA = 10

    /** Los precios se presentan y se cobran con 4 decimales, como `precio_medio`. */
    const val ESCALA_PRECIO = 4

    /** El dinero siempre con 2 decimales y redondeo explicito. */
    const val ESCALA_IMPORTE = 2

    fun precioActual(proyecto: Proyecto): BigDecimal {
        val progreso = proyecto.participacionesEmitidas.divide(
            proyecto.participacionesTotales,
            ESCALA_INTERMEDIA,
            RoundingMode.HALF_UP,
        )
        return proyecto.precioBase
            .multiply(BigDecimal.ONE.add(progreso))
            .setScale(ESCALA_PRECIO, RoundingMode.HALF_UP)
    }

    /** Lo que cuesta un lote a un precio dado, ya redondeado a dinero de verdad. */
    fun importe(participaciones: BigDecimal, precioUnitario: BigDecimal): BigDecimal =
        participaciones.multiply(precioUnitario)
            .setScale(ESCALA_IMPORTE, RoundingMode.HALF_UP)

    /** Participaciones libres de la ronda. */
    fun disponibles(proyecto: Proyecto): BigDecimal =
        proyecto.participacionesTotales.subtract(proyecto.participacionesEmitidas)
}
