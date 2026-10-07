package com.jicp.api.proyecto

import com.jicp.api.alumno.Alumno
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.io.Serializable

/**
 * Clave primaria compuesta (id_alumno, id_proyecto).
 *
 * Sus componentes son nulables porque Hibernate los rellena al persistir, derivandolos
 * de las asociaciones marcadas con `@MapsId`.
 */
@Embeddable
data class AlumnoProyectoId(

    @Column(name = "id_alumno")
    var idAlumno: Int? = null,

    @Column(name = "id_proyecto")
    var idProyecto: Int? = null,
) : Serializable

/**
 * Participacion de un alumno en un proyecto.
 *
 * Un alumno puede figurar una sola vez por proyecto (lo garantiza la clave primaria)
 * y un proyecto tiene como maximo un miembro con rol Creador (lo garantiza el indice
 * parcial `ux_un_creador_por_proyecto`).
 */
@Entity
@Table(name = "alumno_proyecto")
class AlumnoProyecto(

    @MapsId("idAlumno")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    var alumno: Alumno,

    @MapsId("idProyecto")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyecto", nullable = false)
    var proyecto: Proyecto,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    var rol: RolProyecto,

    /** Instancia vacia, no null: Hibernate escribe dentro de ella al derivar la clave. */
    @EmbeddedId
    var clave: AlumnoProyectoId = AlumnoProyectoId(),
) : Persistable<AlumnoProyectoId> {

    @field:Transient
    private var recienCreado: Boolean = true

    override fun getId(): AlumnoProyectoId = clave

    /**
     * Spring Data elige entre `persist` y `merge` segun si el id es nulo, y una clave
     * compuesta nunca lo es. Sin este control, cada alta pasaria por `merge`: un SELECT
     * innecesario y, sobre todo, un camino en el que Hibernate no deriva la clave del `@MapsId`.
     *
     * En las entidades recuperadas de la base de datos arranca en false, porque el
     * constructor sin argumentos que genera el plugin de Kotlin no ejecuta inicializadores.
     */
    override fun isNew(): Boolean = recienCreado

    @PostPersist
    @PostLoad
    fun marcarComoPersistido() {
        recienCreado = false
    }

    /** Los ids se leen de las asociaciones: en un proxy perezoso no disparan consulta. */
    val idAlumno: Int
        get() = requireNotNull(alumno.id) { "Alumno sin persistir" }

    val idProyecto: Int
        get() = requireNotNull(proyecto.id) { "Proyecto sin persistir" }

    val esCreador: Boolean
        get() = rol.id == RolesProyecto.CREADOR
}
