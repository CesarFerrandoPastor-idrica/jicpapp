package com.jicp.api.curso

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CrearCursoRequest(
    @field:NotBlank(message = "el titulo es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val titulo: String,

    @field:NotBlank(message = "la descripcion es obligatoria")
    @field:Size(max = 5000, message = "maximo 5000 caracteres")
    val descripcion: String,

    /**
     * Enlace opcional a un video o documento. Solo http(s): un `javascript:` aqui acabaria
     * en un enlace pulsable en el movil de cada alumno del curso.
     */
    @field:Size(max = 500, message = "maximo 500 caracteres")
    @field:Pattern(regexp = "^https?://\\S+$", message = "debe ser un enlace http o https")
    val urlRecurso: String? = null,

    /**
     * A quien se asigna. Tienen que ser alumnos del centro del profesor; el servidor lo
     * comprueba uno a uno. El profesor no se envia: sale del token.
     */
    @field:NotEmpty(message = "asigna el curso a al menos un alumno")
    val idsAlumnos: List<Int>,
)

/**
 * Cambios en el contenido de un curso. Los alumnos asignados no se tocan aqui.
 * La URL que no se envie (o venga vacia) se quita del curso.
 */
data class ActualizarCursoRequest(
    @field:NotBlank(message = "el titulo es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val titulo: String,

    @field:NotBlank(message = "la descripcion es obligatoria")
    @field:Size(max = 5000, message = "maximo 5000 caracteres")
    val descripcion: String,

    @field:Size(max = 500, message = "maximo 500 caracteres")
    @field:Pattern(regexp = "^https?://\\S+$", message = "debe ser un enlace http o https")
    val urlRecurso: String? = null,
)

/**
 * Un curso tal y como lo ve quien lo pide. Al profesor le interesa cuantos alumnos tiene
 * asignados; al alumno, su progreso. El campo que no aplica viene a null.
 */
data class CursoResponse(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val urlRecurso: String?,
    val profesor: String,
    val alumnosAsignados: Long?,
    val progreso: BigDecimal?,
    val fechaRegistro: LocalDateTime,
)

fun Curso.toResponse(alumnosAsignados: Long? = null, progreso: BigDecimal? = null) = CursoResponse(
    id = requireNotNull(id) { "Curso sin persistir" },
    titulo = titulo,
    descripcion = descripcion,
    urlRecurso = urlRecurso,
    profesor = "${profesor.nombre} ${profesor.apellido}",
    alumnosAsignados = alumnosAsignados,
    progreso = progreso,
    fechaRegistro = fechaRegistro,
)
