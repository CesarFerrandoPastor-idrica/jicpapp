package com.jicp.api.inversion

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface InversionRepository : JpaRepository<Inversion, Int> {

    fun findByProyectoIdAndAlumnoId(idProyecto: Int, idAlumno: Int): Inversion?

    /** La cartera de posiciones de un alumno, para la pantalla de portafolio. */
    @EntityGraph(attributePaths = ["proyecto", "proyecto.categoria", "proyecto.estado"])
    fun findByAlumnoIdOrderByIdDesc(idAlumno: Int): List<Inversion>

    /** Quien ha invertido en un proyecto, para el detalle del mercado. */
    @EntityGraph(attributePaths = ["alumno"])
    fun findByProyectoIdOrderByParticipacionesDesc(idProyecto: Int): List<Inversion>
}

interface MovimientoInversionRepository : JpaRepository<MovimientoInversion, Long> {

    @EntityGraph(attributePaths = ["operacion"])
    fun findByInversionIdOrderByIdDesc(idInversion: Int): List<MovimientoInversion>

    /** El movimiento que genero una operacion contable concreta. Sirve para la idempotencia. */
    @EntityGraph(attributePaths = ["inversion", "inversion.proyecto", "operacion"])
    fun findByOperacionId(idOperacion: Int): MovimientoInversion?
}
