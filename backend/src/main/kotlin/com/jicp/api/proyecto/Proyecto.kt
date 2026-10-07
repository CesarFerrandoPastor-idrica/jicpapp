package com.jicp.api.proyecto

import com.jicp.api.colegio.Colegio
import jakarta.persistence.Column
import jakarta.persistence.Entity
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

@Entity
@Table(name = "proyecto")
class Proyecto(

    @Column(name = "nombre", nullable = false, length = 150)
    var nombre: String,

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    var descripcion: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria", nullable = false)
    var categoria: CategoriaProyecto,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado", nullable = false)
    var estado: EstadoProyecto,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_colegio", nullable = false)
    var colegio: Colegio,

    @Column(name = "inversion_inicial", nullable = false, precision = 12, scale = 2)
    var inversionInicial: BigDecimal = BigDecimal.ZERO,

    /**
     * Precio de la primera participacion. El precio que se paga de verdad lo calcula
     * el servidor a partir de este y de cuantas van emitidas; el cliente nunca lo envia.
     */
    @Column(name = "precio_base", nullable = false, precision = 12, scale = 2)
    var precioBase: BigDecimal = BigDecimal("100.00"),

    /** Tamano de la ronda: referencia para la subida de precio y tope de emision. */
    @Column(name = "participaciones_totales", nullable = false, precision = 14, scale = 4)
    var participacionesTotales: BigDecimal = BigDecimal("100.0000"),

    @Column(name = "participaciones_emitidas", nullable = false, precision = 14, scale = 4)
    var participacionesEmitidas: BigDecimal = BigDecimal.ZERO,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proyecto")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
