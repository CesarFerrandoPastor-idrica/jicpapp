package com.jicp.api.proyecto

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** Equipo de un proyecto: creador, socios y colaboradores. */
@RestController
@RequestMapping("/api/v1")
class MiembroProyectoController(
    private val servicio: MiembroProyectoService,
) {

    @GetMapping("/proyectos/{idProyecto}/miembros")
    fun listar(@PathVariable idProyecto: Int): List<MiembroProyectoResponse> = servicio.listar(idProyecto)

    @PostMapping("/proyectos/{idProyecto}/miembros")
    fun anadir(
        @PathVariable idProyecto: Int,
        @Valid @RequestBody peticion: AnadirMiembroRequest,
    ): ResponseEntity<MiembroProyectoResponse> {
        val miembro = servicio.anadir(idProyecto, peticion)
        return ResponseEntity
            .created(URI.create("/api/v1/proyectos/$idProyecto/miembros/${miembro.idAlumno}"))
            .body(miembro)
    }

    @PutMapping("/proyectos/{idProyecto}/miembros/{idAlumno}")
    fun cambiarRol(
        @PathVariable idProyecto: Int,
        @PathVariable idAlumno: Int,
        @Valid @RequestBody peticion: CambiarRolRequest,
    ): MiembroProyectoResponse = servicio.cambiarRol(idProyecto, idAlumno, peticion)

    @DeleteMapping("/proyectos/{idProyecto}/miembros/{idAlumno}")
    fun eliminar(@PathVariable idProyecto: Int, @PathVariable idAlumno: Int): ResponseEntity<Void> {
        servicio.eliminar(idProyecto, idAlumno)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/alumnos/{idAlumno}/proyectos")
    fun proyectosDeAlumno(@PathVariable idAlumno: Int): List<ProyectoConRolResponse> =
        servicio.proyectosDeAlumno(idAlumno)
}
