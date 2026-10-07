package com.jicp.api.colegio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "colegio")
class Colegio(

    @Column(name = "nombre", nullable = false, length = 150)
    var nombre: String,

    @Column(name = "direccion", nullable = false, length = 255)
    var direccion: String,

    @Column(name = "email", nullable = false, length = 255)
    var email: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_colegio")
    var id: Int? = null,
) {
    /** La fija el servidor en el INSERT; nunca se acepta una fecha del cliente. */
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
