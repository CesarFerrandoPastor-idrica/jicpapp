package com.jicp.api.profesor

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ProfesorRepository : JpaRepository<Profesor, Int> {

    /** El profesor que hay detras del usuario del token. Puente entre el JWT y el dominio. */
    @EntityGraph(attributePaths = ["colegio", "usuario"])
    fun findByUsuarioId(idUsuario: Int): Profesor?

    // El email vive en usuario y la respuesta lo expone, asi que entra en todos los
    // grafos: sin el, cada fila de la pagina dispararia su propia consulta.
    @EntityGraph(attributePaths = ["colegio", "usuario"])
    override fun findAll(pageable: Pageable): Page<Profesor>

    @EntityGraph(attributePaths = ["colegio", "usuario"])
    fun findByColegioId(idColegio: Int, pageable: Pageable): Page<Profesor>

    @EntityGraph(attributePaths = ["colegio", "usuario"])
    override fun findById(id: Int): Optional<Profesor>
}
