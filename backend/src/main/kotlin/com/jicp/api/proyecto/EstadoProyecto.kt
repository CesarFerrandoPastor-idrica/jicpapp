package com.jicp.api.proyecto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Catalogo de estados: Borrador, Publicado, Cerrado. */
@Entity
@Table(name = "estado_proyecto")
class EstadoProyecto(

    @Column(name = "nombre", nullable = false, length = 50, unique = true)
    var nombre: String,

    @Column(name = "descripcion", columnDefinition = "text")
    var descripcion: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado")
    var id: Int? = null,
)

/**
 * Ids fijados en la migracion V2, igual que los de [RolesProyecto]: se necesitan como
 * literales para poder comparar el estado sin traerse la fila del catalogo.
 */
object EstadosProyecto {
    const val BORRADOR = 1
    const val PUBLICADO = 2
    const val CERRADO = 3
}
