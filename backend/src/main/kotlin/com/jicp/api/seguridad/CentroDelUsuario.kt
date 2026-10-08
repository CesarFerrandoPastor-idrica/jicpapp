package com.jicp.api.seguridad

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.shared.SinPermisoException
import org.springframework.stereotype.Component

/**
 * A que centro pertenece quien hace la peticion.
 *
 * Es lo que acota lo que cada uno ve: un alumno o un profesor solo trabajan con su
 * colegio. El centro sale siempre del token (via su ficha de alumno o de profesor),
 * nunca de un parametro de la peticion, que cualquiera podria cambiar.
 */
@Component
class CentroDelUsuario(
    private val alumnos: AlumnoRepository,
    private val profesores: ProfesorRepository,
) {

    /** El id del colegio del usuario, o null si es ADMIN (no pertenece a ninguno). */
    fun de(actor: UsuarioAutenticado): Int? = when (actor.rol) {
        Rol.ALUMNO -> alumnos.findByUsuarioId(actor.idUsuario)?.colegio?.id
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")
        Rol.PROFESOR -> profesores.findByUsuarioId(actor.idUsuario)?.colegio?.id
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de profesor")
        Rol.ADMIN -> null
    }
}
