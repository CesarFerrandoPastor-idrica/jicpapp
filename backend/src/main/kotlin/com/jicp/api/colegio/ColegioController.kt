package com.jicp.api.colegio

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
@RequestMapping("/api/v1/colegios")
class ColegioController(
    private val servicio: ColegioService,
) {

    @GetMapping
    fun listar(
        @RequestParam(required = false) nombre: String?,
        @PageableDefault(size = 20, sort = ["nombre"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): Page<ColegioResponse> = servicio.listar(nombre, pageable)

    @GetMapping("/{id}")
    fun obtener(@PathVariable id: Int): ColegioResponse = servicio.obtener(id)

    /**
     * Los centros los dan de alta los desarrolladores con una cuenta ADMIN; mas adelante,
     * una persona de la empresa desde la web. Ni alumnado ni profesorado crean colegios.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    fun crear(@Valid @RequestBody peticion: CrearColegioRequest): ResponseEntity<ColegioResponse> {
        val creado = servicio.crear(peticion)
        return ResponseEntity.created(URI.create("/api/v1/colegios/${creado.id}")).body(creado)
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    fun actualizar(
        @PathVariable id: Int,
        @Valid @RequestBody peticion: ActualizarColegioRequest,
    ): ColegioResponse = servicio.actualizar(id, peticion)
}
