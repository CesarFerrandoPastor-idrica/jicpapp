package com.jicp.api.proyecto

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.RecursoNoEncontradoException
import com.jicp.api.shared.SinPermisoException
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@Service
@Transactional(readOnly = true)
class ComentarioService(
    private val comentarios: ComentarioProyectoRepository,
    private val proyectos: ProyectoRepository,
    private val alumnos: AlumnoRepository,
    private val profesores: ProfesorRepository,
) {

    fun listar(idProyecto: Int, pagina: Int, tamano: Int): Page<ComentarioResponse> {
        if (!proyectos.existsById(idProyecto)) {
            throw RecursoNoEncontradoException("proyecto", idProyecto)
        }
        return comentarios
            .findByProyectoIdOrderByIdDesc(idProyecto, PageRequest.of(pagina, tamano))
            .map { it.toResponse() }
    }

    /**
     * Comentar un proyecto. Pueden hacerlo el alumnado y el profesorado, siempre de su
     * propio centro. El autor sale del token; el texto se recorta antes de guardar para
     * que la restriccion de la base (texto no vacio) no salte por espacios sueltos.
     */
    @Transactional
    fun comentar(
        idProyecto: Int,
        actor: UsuarioAutenticado,
        peticion: CrearComentarioRequest,
    ): ComentarioResponse {
        val proyecto = proyectos.findById(idProyecto)
            .orElseThrow { RecursoNoEncontradoException("proyecto", idProyecto) }

        val comentario = ComentarioProyecto(proyecto = proyecto, texto = peticion.texto.trim())
        val centroDelAutor = when (actor.rol) {
            Rol.ALUMNO -> {
                val alumno = alumnos.findByUsuarioId(actor.idUsuario)
                    ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")
                comentario.alumno = alumno
                alumno.colegio.id
            }
            Rol.PROFESOR -> {
                val profesor = profesores.findByUsuarioId(actor.idUsuario)
                    ?: throw SinPermisoException("El usuario autenticado no tiene ficha de profesor")
                comentario.profesor = profesor
                profesor.colegio.id
            }
            Rol.ADMIN -> throw SinPermisoException("Comentan el alumnado y el profesorado del centro")
        }

        // Mismo centro: nadie comenta el mercado de otro colegio.
        if (proyecto.colegio.id != centroDelAutor) {
            throw SinPermisoException("Solo puedes comentar proyectos de tu propio centro")
        }

        return comentarios.save(comentario).toResponse()
    }
}

@RestController
@RequestMapping("/api/v1/proyectos/{idProyecto}/comentarios")
class ComentarioController(
    private val servicio: ComentarioService,
) {

    /** El hilo de un proyecto. Basta con tener sesion. */
    @GetMapping
    fun listar(
        @PathVariable idProyecto: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<ComentarioResponse> = servicio.listar(idProyecto, page, size.coerceIn(1, 100))

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ALUMNO', 'PROFESOR')")
    @PostMapping
    fun comentar(
        @PathVariable idProyecto: Int,
        @Valid @RequestBody peticion: CrearComentarioRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ResponseEntity<ComentarioResponse> {
        val creado = servicio.comentar(idProyecto, actor, peticion)
        return ResponseEntity
            .created(URI.create("/api/v1/proyectos/$idProyecto/comentarios/${creado.id}"))
            .body(creado)
    }
}
