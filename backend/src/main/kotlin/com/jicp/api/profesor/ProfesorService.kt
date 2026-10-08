package com.jicp.api.profesor

import com.jicp.api.colegio.ColegioService
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.UsuarioAutenticado
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
    private val colegios: ColegioService,
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
     * Da de alta a un profesor en el centro indicado.
     *
     * Las altas son de ADMIN (lo comprueba @PreAuthorize en el controlador): de momento
     * las hacen los desarrolladores y mas adelante alguien de la empresa desde la web.
     */
    @Transactional
    fun crear(peticion: CrearProfesorRequest): ProfesorResponse {
        val colegio = colegios.buscarOFallar(peticion.idColegio)

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
            colegio = colegio,
        )
        return repositorio.save(profesor).toResponse()
    }

    /** Un admin edita a cualquiera; un profesor, solo a alguien de su propio centro. */
    @Transactional
    fun actualizar(
        id: Int,
        peticion: ActualizarProfesorRequest,
        actor: UsuarioAutenticado,
    ): ProfesorResponse {
        val profesor = buscarOFallar(id)

        if (actor.rol != Rol.ADMIN) {
            val suyo = actorOFallar(actor.idUsuario)
            if (profesor.colegio.id != suyo.colegio.id) {
                throw SinPermisoException("Solo puedes editar profesorado de tu propio centro")
            }
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
