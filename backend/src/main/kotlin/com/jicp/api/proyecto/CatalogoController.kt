package com.jicp.api.proyecto

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Catalogos de solo lectura que alimentan los filtros del mercado y los
 * desplegables del formulario de alta de proyecto en la app.
 */
@RestController
@RequestMapping("/api/v1")
class CatalogoController(
    private val servicio: ProyectoService,
) {

    @GetMapping("/categorias")
    fun categorias(): List<CatalogoResponse> = servicio.listarCategorias()

    @GetMapping("/estados-proyecto")
    fun estados(): List<CatalogoResponse> = servicio.listarEstados()

    @GetMapping("/roles-proyecto")
    fun roles(): List<CatalogoResponse> = servicio.listarRoles()
}
