package com.jicp.api.colegio

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ColegioRepository : JpaRepository<Colegio, Int> {

    fun findByNombreContainingIgnoreCase(nombre: String, pageable: Pageable): Page<Colegio>
}
