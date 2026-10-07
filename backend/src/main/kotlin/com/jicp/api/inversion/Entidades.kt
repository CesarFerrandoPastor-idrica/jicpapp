package com.jicp.api.inversion

import com.jicp.api.alumno.Alumno
import com.jicp.api.contabilidad.OperacionJicp
import com.jicp.api.proyecto.Proyecto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

enum class TipoMovimiento { COMPRA, VENTA }

/**
 * La posicion de un alumno en un proyecto: cuantas participaciones tiene y a que
 * precio medio las compro.
 *
 * Hay una sola fila por (proyecto, alumno): las compras sucesivas acumulan
 * participaciones y recalculan el precio medio, en lugar de crear posiciones sueltas.
 * El detalle de cada compra vive en [MovimientoInversion].
 */
@Entity
@Table(name = "inversion")
class Inversion(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyecto", nullable = false)
    var proyecto: Proyecto,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    var alumno: Alumno,

    @Column(name = "participaciones", nullable = false, precision = 14, scale = 4)
    var participaciones: BigDecimal = BigDecimal.ZERO,

    /**
     * Precio medio de adquisicion. Con precio variable es lo unico que permite saber si
     * la posicion gana o pierde respecto al precio actual del proyecto.
     */
    @Column(name = "precio_medio", nullable = false, precision = 12, scale = 4)
    var precioMedio: BigDecimal = BigDecimal.ZERO,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inversion")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}

/**
 * El detalle bursatil de una compra, enlazado a la operacion contable que movio el
 * dinero. Ese enlace es lo que permite auditar una posicion contra el libro.
 */
@Entity
@Table(name = "movimiento_inversion")
class MovimientoInversion(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_inversion", nullable = false)
    var inversion: Inversion,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_operacion", nullable = false)
    var operacion: OperacionJicp,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    var tipo: TipoMovimiento,

    @Column(name = "participaciones", nullable = false, precision = 14, scale = 4)
    var participaciones: BigDecimal,

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 4)
    var precioUnitario: BigDecimal,

    @Column(name = "importe", nullable = false, precision = 12, scale = 2)
    var importe: BigDecimal,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
