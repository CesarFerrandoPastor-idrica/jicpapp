package com.jicp.api.proyecto

import com.jicp.api.alumno.AlumnoService
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.RecursoNoEncontradoException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Socios y colaboradores de un proyecto.
 *
 * El rol Creador no se gestiona aqui: se asigna al dar de alta el proyecto y no se
 * transfiere ni se retira, porque de el cuelgan la autoria y (mas adelante) la
 * tesoreria del proyecto.
 */
@Service
@Transactional(readOnly = true)
class MiembroProyectoService(
    private val miembros: AlumnoProyectoRepository,
    private val proyectos: ProyectoService,
    private val alumnos: AlumnoService,
) {

    fun listar(idProyecto: Int): List<MiembroProyectoResponse> {
        proyectos.buscarOFallar(idProyecto)
        return miembros.findByProyectoId(idProyecto)
            .sortedWith(compareBy({ it.rol.id }, { it.alumno.apellido }, { it.alumno.nombre }))
            .map { it.toMiembroResponse() }
    }

    /** Proyectos en los que participa un alumno, con el papel que desempeña en cada uno. */
    fun proyectosDeAlumno(idAlumno: Int): List<ProyectoConRolResponse> {
        alumnos.buscarOFallar(idAlumno)
        val vinculos = miembros.findByAlumnoId(idAlumno)
        val creadores = proyectos.creadoresDe(vinculos.map { it.idProyecto })
        return vinculos
            .sortedByDescending { it.proyecto.fechaRegistro }
            .map {
                ProyectoConRolResponse(
                    rol = it.rol.toResponse(),
                    proyecto = it.proyecto.toResponse(creadores[it.idProyecto]),
                )
            }
    }

    @Transactional
    fun anadir(idProyecto: Int, peticion: AnadirMiembroRequest): MiembroProyectoResponse {
        if (peticion.idRol == RolesProyecto.CREADOR) {
            throw ConflictoException(
                "El rol Creador se asigna al dar de alta el proyecto y no puede concederse despues",
            )
        }

        val proyecto = proyectos.buscarOFallar(idProyecto)
        val alumno = alumnos.buscarOFallar(peticion.idAlumno)
        val rol = proyectos.rolOFallar(peticion.idRol)

        if (alumno.colegio.id != proyecto.colegio.id) {
            throw ConflictoException("El alumno pertenece a otro colegio que el proyecto")
        }
        if (miembros.findByProyectoIdAndAlumnoId(idProyecto, peticion.idAlumno) != null) {
            throw ConflictoException("El alumno ya participa en este proyecto")
        }

        return miembros.save(AlumnoProyecto(alumno = alumno, proyecto = proyecto, rol = rol)).toMiembroResponse()
    }

    @Transactional
    fun cambiarRol(idProyecto: Int, idAlumno: Int, peticion: CambiarRolRequest): MiembroProyectoResponse {
        val vinculo = vinculoOFallar(idProyecto, idAlumno)

        if (vinculo.esCreador) {
            throw ConflictoException("El creador del proyecto no puede cambiar de rol")
        }
        if (peticion.idRol == RolesProyecto.CREADOR) {
            throw ConflictoException("La autoria de un proyecto no se transfiere")
        }

        vinculo.rol = proyectos.rolOFallar(peticion.idRol)
        return vinculo.toMiembroResponse()
    }

    @Transactional
    fun eliminar(idProyecto: Int, idAlumno: Int) {
        val vinculo = vinculoOFallar(idProyecto, idAlumno)

        if (vinculo.esCreador) {
            throw ConflictoException("El creador no puede abandonar su propio proyecto")
        }
        miembros.delete(vinculo)
    }

    private fun vinculoOFallar(idProyecto: Int, idAlumno: Int): AlumnoProyecto =
        miembros.findByProyectoIdAndAlumnoId(idProyecto, idAlumno)
            ?: throw RecursoNoEncontradoException("participacion del alumno $idAlumno en el proyecto", idProyecto)
}
