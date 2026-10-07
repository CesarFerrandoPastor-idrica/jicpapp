package com.jicp.api.proyecto

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.seguridad.UsuarioAutenticado
import com.jicp.api.shared.SinPermisoException
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/proyectos")
class ProyectoController(
    private val servicio: ProyectoService,
    private val alumnos: AlumnoRepository,
) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) idColegio: Int?,
        @RequestParam(required = false) idCategoria: Int?,
        @RequestParam(required = false) idEstado: Int?,
        @PageableDefault(size = 20, sort = ["fechaRegistro"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<ProyectoResponse> = servicio.listar(idColegio, idCategoria, idEstado, pageable)

    @GetMapping("/{id}")
    fun obtener(@PathVariable id: Int): ProyectoResponse = servicio.obtener(id)

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ALUMNO')")
    @PostMapping
    fun crear(
        @Valid @RequestBody peticion: CrearProyectoRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ResponseEntity<ProyectoResponse> {
        val idAlumno = alumnos.findByUsuarioId(actor.idUsuario)?.id
            ?: throw SinPermisoException("El usuario autenticado no tiene ficha de alumno")

        val creado = servicio.crear(peticion, idAlumno)
        return ResponseEntity.created(URI.create("/api/v1/proyectos/${creado.id}")).body(creado)
    }

    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarProyectoRequest,
    ): ProyectoResponse = servicio.actualizar(id, peticion)
}
