package com.jicp.api.profesor

import com.jicp.api.seguridad.UsuarioAutenticado
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
@RequestMapping("/api/v1/profesores")
class ProfesorController(
    private val servicio: ProfesorService,
) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) idColegio: Int?,
        @PageableDefault(size = 20, sort = ["apellido"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): Page<ProfesorResponse> = servicio.listar(idColegio, pageable)

    @GetMapping("/{id}")
    fun obtener(@PathVariable id: Int): ProfesorResponse = servicio.obtener(id)

    /**
     * A un profesor lo da de alta otro profesor, y en su mismo centro.
     *
     * Este endpoint crea credenciales, asi que es de los pocos que ya esta cerrado
     * mientras el resto del CRUD sigue abierto: dejar abierta la creacion de cuentas
     * es distinto de dejar abierta la lectura de un catalogo.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('PROFESOR')")
    @PostMapping
    fun crear(
        @Valid @RequestBody peticion: CrearProfesorRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ResponseEntity<ProfesorResponse> {
        val creado = servicio.crear(peticion, actor.idUsuario)
        return ResponseEntity.created(URI.create("/api/v1/profesores/${creado.id}")).body(creado)
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('PROFESOR')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarProfesorRequest,
        @AuthenticationPrincipal actor: UsuarioAutenticado,
    ): ProfesorResponse = servicio.actualizar(id, peticion, actor.idUsuario)
}
