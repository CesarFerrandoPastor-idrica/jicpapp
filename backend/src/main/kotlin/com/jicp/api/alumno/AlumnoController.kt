package com.jicp.api.alumno

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
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

    @PostMapping
    fun crear(@Valid @RequestBody peticion: CrearAlumnoRequest): ResponseEntity<AlumnoResponse> {
        val creado = servicio.crear(peticion)
        return ResponseEntity.created(URI.create("/api/v1/alumnos/${creado.id}")).body(creado)
    }

    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarAlumnoRequest,
    ): AlumnoResponse = servicio.actualizar(id, peticion)
}
