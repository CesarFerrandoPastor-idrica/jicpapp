package com.jicp.api.proyecto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Catalogo de roles dentro de un proyecto: Creador, Socio, Colaborador. */
@Entity
@Table(name = "rol_proyecto")
class RolProyecto(

    @Column(name = "nombre", nullable = false, length = 50, unique = true)
    var nombre: String,

    @Column(name = "descripcion", columnDefinition = "text")
    var descripcion: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    var id: Int? = null,
)

/**
 * Los ids del catalogo se fijan en la migracion V3 y no cambian: el indice parcial
 * `ux_un_creador_por_proyecto` depende del literal 1, asi que renumerarlos rompería
 * la garantia de "un solo creador por proyecto".
 */
object RolesProyecto {
    const val CREADOR = 1
    const val SOCIO = 2
    const val COLABORADOR = 3
}
