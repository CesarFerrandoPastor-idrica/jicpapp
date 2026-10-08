package com.jicp.api.curso

import com.jicp.api.seguridad.UsuarioAutenticado
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/cursos")
@SecurityRequirement(name = "bearerAuth")
class CursoController(
    private val servicio: CursoService,
) {

    /** Los cursos del profesor que los imparte, o los asignados al alumno que pregunta. */
    @PreAuthorize("hasAnyRole('PROFESOR', 'ALUMNO')")
    @GetMapping
    fun listar(@AuthenticationPrincipal actor: UsuarioAutenticado): List<CursoResponse> =
        servicio.listar(actor)

    /** Crear un curso y asignarlo a alumnos del propio centro. Solo profesorado. */
    @PreAuthorize("hasRole('PROFESOR')")
    @PostMapping
    fun crear(
        @Valid @RequestBody peticion: CrearCursoRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ResponseEntity<CursoResponse> {
        val creado = servicio.crear(peticion, actor)
        return ResponseEntity.created(URI.create("/api/v1/cursos/${creado.id}")).body(creado)
    }

    /** Editar el contenido de un curso. Solo el profesor que lo imparte. */
    @PreAuthorize("hasRole('PROFESOR')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarCursoRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): CursoResponse = servicio.actualizar(id, peticion, actor)
}
