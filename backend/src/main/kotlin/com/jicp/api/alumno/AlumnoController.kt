package com.jicp.api.alumno

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/api/v1/alumnos")
class AlumnoController(
    private val servicio: AlumnoService,
) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) idColegio: Int?,
        @PageableDefault(size = 20, sort = ["apellido"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): Page<AlumnoResponse> = servicio.listar(idColegio, pageable)

    @GetMapping("/{id}")
    fun obtener(@PathVariable id: Int): AlumnoResponse = servicio.obtener(id)

    /**
     * Alta de alumno: crea sus credenciales, su cartera y le concede el saldo inicial.
     *
     * Solo ADMIN. Hoy las altas las hacen los desarrolladores; si mas adelante se registra
     * el propio alumnado, el profesorado o una web externa, basta con cambiar quien entra
     * aqui. Lo que no cambia es que el saldo inicial lo pone el servidor, no la peticion.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    fun crear(@Valid @RequestBody peticion: CrearAlumnoRequest): ResponseEntity<AlumnoResponse> {
        val creado = servicio.crear(peticion)
        return ResponseEntity.created(URI.create("/api/v1/alumnos/${creado.id}")).body(creado)
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarAlumnoRequest,
    ): AlumnoResponse = servicio.actualizar(id, peticion)
}
