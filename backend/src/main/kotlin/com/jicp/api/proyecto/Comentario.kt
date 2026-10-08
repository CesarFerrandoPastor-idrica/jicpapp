package com.jicp.api.proyecto

import com.jicp.api.alumno.Alumno
import com.jicp.api.profesor.Profesor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

@Entity
@Table(name = "comentario_proyecto")
class ComentarioProyecto(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyecto", nullable = false)
    var proyecto: Proyecto,

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    var texto: String,

    /**
     * Quien lo escribio: un alumno o un profesor, nunca los dos ni ninguno. Lo garantiza
     * el CHECK `un_solo_autor` de la base.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_alumno")
    var alumno: Alumno? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_profesor")
    var profesor: Profesor? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comentario")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}

interface ComentarioProyectoRepository : JpaRepository<ComentarioProyecto, Int> {

    @EntityGraph(attributePaths = ["alumno", "profesor"])
    fun findByProyectoIdOrderByIdDesc(idProyecto: Int, pageable: Pageable): Page<ComentarioProyecto>
}

data class CrearComentarioRequest(
    @field:NotBlank(message = "el comentario no puede estar vacio")
    @field:Size(max = 2000, message = "maximo 2000 caracteres")
    val texto: String,
)

/**
 * Un comentario del hilo. De `idAlumno` e `idProfesor` viene relleno exactamente uno:
 * el de quien lo escribio. `deProfesor` lo dice sin tener que mirar cual.
 */
data class ComentarioResponse(
    val id: Int,
    val idProyecto: Int,
    val idAlumno: Int?,
    val idProfesor: Int?,
    val deProfesor: Boolean,
    val nombre: String,
    val apellido: String,
    val texto: String,
    val fecha: LocalDateTime,
)

fun ComentarioProyecto.toResponse(): ComentarioResponse {
    val delAlumno = alumno
    val delProfesor = profesor
    return ComentarioResponse(
        id = requireNotNull(id) { "Comentario sin persistir" },
        idProyecto = requireNotNull(proyecto.id) { "Proyecto sin persistir" },
        idAlumno = delAlumno?.id,
        idProfesor = delProfesor?.id,
        deProfesor = delProfesor != null,
        nombre = delAlumno?.nombre ?: delProfesor?.nombre ?: "",
        apellido = delAlumno?.apellido ?: delProfesor?.apellido ?: "",
        texto = texto,
        fecha = fechaRegistro,
    )
}
