package com.jicp.api.proyecto

import com.jicp.api.alumno.Alumno
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    var alumno: Alumno,

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    var texto: String,

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

    @EntityGraph(attributePaths = ["alumno"])
    fun findByProyectoIdOrderByIdDesc(idProyecto: Int, pageable: Pageable): Page<ComentarioProyecto>
}

data class CrearComentarioRequest(
    @field:NotBlank(message = "el comentario no puede estar vacio")
    @field:Size(max = 2000, message = "maximo 2000 caracteres")
    val texto: String,
)

data class ComentarioResponse(
    val id: Int,
    val idProyecto: Int,
    val idAlumno: Int,
    val nombre: String,
    val apellido: String,
    val texto: String,
    val fecha: LocalDateTime,
)

fun ComentarioProyecto.toResponse() = ComentarioResponse(
    id = requireNotNull(id) { "Comentario sin persistir" },
    idProyecto = requireNotNull(proyecto.id) { "Proyecto sin persistir" },
    idAlumno = requireNotNull(alumno.id) { "Alumno sin persistir" },
    nombre = alumno.nombre,
    apellido = alumno.apellido,
    texto = texto,
    fecha = fechaRegistro,
)
