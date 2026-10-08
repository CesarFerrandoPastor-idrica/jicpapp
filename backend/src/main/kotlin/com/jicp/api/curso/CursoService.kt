package com.jicp.api.curso

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.profesor.Profesor
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.RecursoNoEncontradoException
import com.jicp.api.shared.SinPermisoException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CursoService(
    private val cursos: CursoRepository,
    private val matriculas: MatriculaRepository,
    private val profesores: ProfesorRepository,
    private val alumnos: AlumnoRepository,
) {

    /**
     * Crea un curso y lo asigna a los alumnos indicados, todo en una transaccion.
     *
     * El profesor sale del token. Los alumnos tienen que ser **de su centro**: si alguno
     * no lo es, no se crea nada (403). Un id repetido cuenta una sola vez.
     */
    @Transactional
    fun crear(peticion: CrearCursoRequest, actor: UsuarioAutenticado): CursoResponse {
        val profesor = profesorDe(actor)

        val ids = peticion.idsAlumnos.distinct()
        val encontrados = alumnos.findAllById(ids)
        val faltan = ids - encontrados.mapNotNull { it.id }.toSet()
        if (faltan.isNotEmpty()) {
            throw RecursoNoEncontradoException("alumno", faltan.first())
        }

        val deOtroCentro = encontrados.filter { it.colegio.id != profesor.colegio.id }
        if (deOtroCentro.isNotEmpty()) {
            throw SinPermisoException("Solo puedes asignar cursos a alumnos de tu centro")
        }

        val curso = Curso(
            titulo = peticion.titulo.trim(),
            descripcion = peticion.descripcion.trim(),
            urlRecurso = peticion.urlRecurso?.trim()?.ifBlank { null },
            profesor = profesor,
        )
        encontrados.forEach { curso.matriculas.add(Matricula(curso = curso, alumno = it)) }

        val guardado = cursos.save(curso)
        return guardado.toResponse(alumnosAsignados = encontrados.size.toLong())
    }

    /**
     * Cambia el contenido de un curso: titulo, descripcion y enlace. Solo puede hacerlo el
     * profesor que lo imparte; para cualquier otro, aunque sea de su mismo centro, `403`.
     */
    @Transactional
    fun actualizar(id: Int, peticion: ActualizarCursoRequest, actor: UsuarioAutenticado): CursoResponse {
        val profesor = profesorDe(actor)
        val curso = cursos.findById(id).orElseThrow { RecursoNoEncontradoException("curso", id) }
        if (curso.profesor.id != profesor.id) {
            throw SinPermisoException("Solo puedes editar los cursos que impartes")
        }

        curso.titulo = peticion.titulo.trim()
        curso.descripcion = peticion.descripcion.trim()
        curso.urlRecurso = peticion.urlRecurso?.trim()?.ifBlank { null }
        return curso.toResponse(alumnosAsignados = matriculas.countByCursoId(id))
    }

    /**
     * "Mis cursos". El profesor ve los que imparte, con cuantos alumnos tiene asignados;
     * el alumno, los que le han asignado, con su progreso.
     */
    fun listar(actor: UsuarioAutenticado): List<CursoResponse> = when (actor.rol) {
        Rol.PROFESOR -> {
            val profesor = profesorDe(actor)
            cursos.findByProfesorIdOrderByIdDesc(profesor.id!!).map {
                it.toResponse(alumnosAsignados = matriculas.countByCursoId(it.id!!))
            }
        }
        Rol.ALUMNO -> {
            val alumno = alumnos.findByUsuarioId(actor.idUsuario)
                ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")
            matriculas.findByAlumnoIdOrderByCursoIdDesc(alumno.id!!).map {
                it.curso.toResponse(progreso = it.progreso)
            }
        }
        Rol.ADMIN -> throw SinPermisoException("Los cursos son del profesorado y del alumnado")
    }

    private fun profesorDe(actor: UsuarioAutenticado): Profesor =
        profesores.findByUsuarioId(actor.idUsuario)
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de profesor")
}
