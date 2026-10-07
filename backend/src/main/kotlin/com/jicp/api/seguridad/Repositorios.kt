package com.jicp.api.seguridad

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UsuarioRepository : JpaRepository<Usuario, Int> {

    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): Usuario?
}

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByHashToken(hashToken: String): RefreshToken?

    /**
     * Corta todas las sesiones abiertas de un usuario. Se llama al detectar que alguien
     * reutiliza un token ya rotado: como no se sabe si el ladron es quien presenta el
     * token viejo o quien tiene el nuevo, se invalidan los dos.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update RefreshToken t
        set t.fechaRevocacion = :ahora
        where t.usuario.id = :idUsuario and t.fechaRevocacion is null
        """,
    )
    fun revocarSesionesDe(
        @Param("idUsuario") idUsuario: Int,
        @Param("ahora") ahora: LocalDateTime,
    ): Int
}
