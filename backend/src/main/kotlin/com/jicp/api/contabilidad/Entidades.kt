package com.jicp.api.contabilidad

import com.jicp.api.alumno.Alumno
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

enum class TipoCuenta {
    /** La cartera de un alumno. Nunca en negativo. */
    CARTERA_ALUMNO,

    /** El dinero recaudado por un proyecto. Nunca en negativo. */
    TESORERIA_PROYECTO,

    /**
     * Contrapartida de la creacion de dinero. Su saldo negativo **es** la masa
     * monetaria en circulacion: gracias a ella, conceder saldo inicial tambien
     * cuadra a cero en lugar de aparecer por arte de magia.
     */
    EMISION_SISTEMA,
}

enum class TipoOperacion {
    CONCESION_INICIAL,
    CREACION_PROYECTO,
    INVERSION,
    DESINVERSION,
    RENDIMIENTO,
    AJUSTE_ADMIN,
    MIGRACION,
}

@Entity
@Table(name = "cuenta")
class Cuenta(

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    var tipo: TipoCuenta,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_alumno")
    var alumno: Alumno? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proyecto")
    var proyecto: Proyecto? = null,

    /**
     * Saldo cacheado. Redundante con la suma de los apuntes a proposito: no se puede
     * poner un CHECK sobre un SUM(), y la restriccion de la base es la ultima linea
     * de defensa contra un descubierto.
     */
    @Column(name = "saldo", nullable = false, precision = 12, scale = 2)
    var saldo: BigDecimal = BigDecimal.ZERO,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuenta")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}

/**
 * Una operacion semantica ("invertir 750 en el proyecto 12"), con sus apuntes colgando.
 * Es la unidad que el cliente pide y sobre la que actua la idempotencia.
 */
@Entity
@Table(name = "operacion_jicp")
class OperacionJicp(

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    var tipo: TipoOperacion,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno_actor", nullable = false)
    var actor: Alumno,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proyecto")
    var proyecto: Proyecto? = null,

    @Column(name = "clave_idempotencia", nullable = false, length = 64)
    var claveIdempotencia: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_operacion")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}

/**
 * Un apunte del libro. **Append-only**: no se actualiza ni se borra nunca.
 * Corregir un error es anadir una operacion compensatoria, igual que en contabilidad real.
 */
@Entity
@Table(name = "apunte_jicp")
class ApunteJicp(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_operacion", nullable = false)
    var operacion: OperacionJicp,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuenta", nullable = false)
    var cuenta: Cuenta,

    /** Negativo sale, positivo entra. Los apuntes de una operacion suman exactamente cero. */
    @Column(name = "importe", nullable = false, precision = 12, scale = 2)
    var importe: BigDecimal,

    @Column(name = "saldo_posterior", nullable = false, precision = 12, scale = 2)
    var saldoPosterior: BigDecimal,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_apunte")
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
