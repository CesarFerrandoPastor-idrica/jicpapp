package com.jicp.api.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/**
 * Un refresh token emitido. Se guarda el SHA-256 del valor, no el valor: quien consiga
 * leer la tabla no puede renovar la sesion de nadie.
 *
 * La rotacion es el motivo de que exista esta tabla en vez de un segundo JWT: un token
 * firmado no se puede invalidar antes de que expire, y aqui hace falta poder quemarlo
 * en cuanto se usa.
 */
@Entity
@Table(name = "refresh_token")
class RefreshToken(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    var usuario: Usuario,

    @Column(name = "hash_token", nullable = false, length = 64, unique = true)
    var hashToken: String,

    @Column(name = "fecha_expiracion", nullable = false)
    var fechaExpiracion: LocalDateTime,

    /** NULL mientras sigue vivo. Se rellena al rotarlo o al cerrar sesion. */
    @Column(name = "fecha_revocacion")
    var fechaRevocacion: LocalDateTime? = null,

    /** El token que lo reemplazo al rotar; deja la cadena de la sesion reconstruible. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sustituto")
    var sustituto: RefreshToken? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    var id: Long? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()

    fun estaVivo(ahora: LocalDateTime): Boolean =
        fechaRevocacion == null && fechaExpiracion.isAfter(ahora)
}
