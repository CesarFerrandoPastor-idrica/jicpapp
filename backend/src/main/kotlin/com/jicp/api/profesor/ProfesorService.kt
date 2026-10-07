package com.jicp.api.profesor

import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import com.jicp.api.seguridad.UsuarioRepository
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.RecursoNoEncontradoException
import com.jicp.api.shared.SinPermisoException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProfesorService(
    private val repositorio: ProfesorRepository,
    private val usuarios: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun listar(idColegio: Int?, pageable: Pageable): Page<ProfesorResponse> {
        val pagina = if (idColegio == null) {
            repositorio.findAll(pageable)
        } else {
            repositorio.findByColegioId(idColegio, pageable)
        }
        return pagina.map { it.toResponse() }
    }

    fun obtener(id: Int): ProfesorResponse = buscarOFallar(id).toResponse()

    /**
     * Da de alta a un companero de claustro.
     *
     * A un profesor lo crea otro profesor: no hay figura de administrador en este
     * producto, asi que inventarle un rol solo para custodiar este endpoint seria
     * anadir una persona que no existe en ningun sitio de la aplicacion.
     *
     * El centro **se hereda de quien da el alta** y no se acepta en la peticion, igual
     * que el colegio de un proyecto sale de su alumno creador: si viniera en el body,
     * un docente podria darse de alta companeros en un centro que no es el suyo.
     */
    @Transactional
    fun crear(peticion: CrearProfesorRequest, idUsuarioActor: Int): ProfesorResponse {
        val actor = actorOFallar(idUsuarioActor)

        // En minusculas para que el login no dependa de como escriba el email quien lo teclea.
        val email = peticion.email.trim().lowercase()

        // El email es unico en todo el sistema, no solo entre profesores: un alumno y un
        // profesor tampoco pueden compartirlo, o el login no sabria a quien dejar entrar.
        // El indice unico de usuario sigue siendo la garantia real.
        if (usuarios.existsByEmail(email)) {
            throw ConflictoException("Ya existe un usuario registrado con el email $email")
        }

        val profesor = Profesor(
            nombre = peticion.nombre.trim(),
            apellido = peticion.apellido.trim(),
            usuario = Usuario(
                email = email,
                passwordHash = passwordEncoder.encode(peticion.password),
                rol = Rol.PROFESOR,
            ),
            colegio = actor.colegio,
        )
        return repositorio.save(profesor).toResponse()
    }

    /** Solo se edita a alguien del propio centro. */
    @Transactional
    fun actualizar(
        id: Int,
        peticion: ActualizarProfesorRequest,
        idUsuarioActor: Int,
    ): ProfesorResponse {
        val actor = actorOFallar(idUsuarioActor)
        val profesor = buscarOFallar(id)

        if (profesor.colegio.id != actor.colegio.id) {
            throw SinPermisoException("Solo puedes editar profesorado de tu propio centro")
        }

        profesor.nombre = peticion.nombre.trim()
        profesor.apellido = peticion.apellido.trim()
        return profesor.toResponse()
    }

    fun buscarOFallar(id: Int): Profesor =
        repositorio.findById(id).orElseThrow { RecursoNoEncontradoException("profesor", id) }

    /**
     * El profesor que hay detras del token. El rol ya lo ha comprobado @PreAuthorize;
     * esto ademas garantiza que tiene ficha en la tabla, porque de ella sale el centro.
     */
    private fun actorOFallar(idUsuarioActor: Int): Profesor =
        repositorio.findByUsuarioId(idUsuarioActor)
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de profesor")
}
