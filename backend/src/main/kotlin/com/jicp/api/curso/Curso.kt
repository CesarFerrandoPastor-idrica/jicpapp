package com.jicp.api.curso

import com.jicp.api.alumno.Alumno
import com.jicp.api.profesor.Profesor
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

/** Un curso que imparte un profesor. Su centro es el del profesor. */
@Entity
@Table(name = "curso")
class Curso(

    @Column(name = "titulo", nullable = false, length = 150)
    var titulo: String,

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    var descripcion: String,

    @Column(name = "url_recurso", length = 500)
    var urlRecurso: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesor", nullable = false)
    var profesor: Profesor,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()

    /**
     * Las matriculas nacen con el curso. Se guardan en cascada al persistirlo, en la
     * misma transaccion: un curso nunca existe con la mitad de su clase asignada.
     */
    @OneToMany(mappedBy = "curso", cascade = [CascadeType.PERSIST])
    var matriculas: MutableList<Matricula> = mutableListOf()
}

/**
 * Clave primaria compuesta (id_curso, id_alumno). Nulable porque Hibernate la rellena
 * al persistir, derivandola de las asociaciones marcadas con `@MapsId`.
 */
@Embeddable
data class MatriculaId(

    @Column(name = "id_curso")
    var idCurso: Int? = null,

    @Column(name = "id_alumno")
    var idAlumno: Int? = null,
) : Serializable

/** Un alumno asignado a un curso, con su progreso. */
@Entity
@Table(name = "matricula")
class Matricula(

    @MapsId("idCurso")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curso", nullable = false)
    var curso: Curso,

    @MapsId("idAlumno")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    var alumno: Alumno,

    /** Porcentaje completado, de 0 a 100. */
    @Column(name = "progreso", nullable = false, precision = 5, scale = 2)
    var progreso: BigDecimal = BigDecimal.ZERO,

    /** Instancia vacia, no null: Hibernate escribe dentro de ella al derivar la clave. */
    @EmbeddedId
    var clave: MatriculaId = MatriculaId(),
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}

interface CursoRepository : JpaRepository<Curso, Int> {

    /** Los cursos de un profesor, los mas recientes primero. */
    @EntityGraph(attributePaths = ["profesor"])
    fun findByProfesorIdOrderByIdDesc(idProfesor: Int): List<Curso>
}

interface MatriculaRepository : JpaRepository<Matricula, MatriculaId> {

    /** Los cursos de un alumno, con su progreso. */
    @EntityGraph(attributePaths = ["curso", "curso.profesor"])
    fun findByAlumnoIdOrderByCursoIdDesc(idAlumno: Int): List<Matricula>

    fun countByCursoId(idCurso: Int): Long
}
