package com.jicp.api.alumno

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AlumnoRepository : JpaRepository<Alumno, Int> {

    /** El alumno que hay detras del usuario del token. Es el puente entre el JWT y el dominio. */
    @EntityGraph(attributePaths = ["colegio", "usuario"])
    fun findByUsuarioId(idUsuario: Int): Alumno?

    // El email vive en usuario y la respuesta lo expone, asi que entra en todos los grafos:
    // sin el, cada fila de la pagina dispararia su propia consulta.
    @EntityGraph(attributePaths = ["colegio", "usuario"])
    override fun findAll(pageable: Pageable): Page<Alumno>

    @EntityGraph(attributePaths = ["colegio", "usuario"])
    fun findByColegioId(idColegio: Int, pageable: Pageable): Page<Alumno>

    /** Todo el alumnado de un centro, sin paginar: una clase cabe entera en una respuesta. */
    fun findByColegioIdOrderByApellidoAscNombreAsc(idColegio: Int): List<Alumno>

    @EntityGraph(attributePaths = ["colegio", "usuario"])
    override fun findById(id: Int): Optional<Alumno>
}
