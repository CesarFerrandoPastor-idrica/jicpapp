package com.jicp.api.profesor

import com.jicp.api.colegio.Colegio
import com.jicp.api.seguridad.Usuario
import jakarta.persistence.CascadeType
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

@Entity
@Table(name = "profesor")
class Profesor(

    @Column(name = "nombre", nullable = false, length = 100)
    var nombre: String,

    @Column(name = "apellido", nullable = false, length = 150)
    var apellido: String,

    /**
     * Credenciales y rol, igual que en alumno: el email y la contrasena no se
     * duplican aqui. Lo unico que distingue a un profesor de un alumno a la hora
     * de entrar es el rol de su usuario.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = [CascadeType.PERSIST])
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    var usuario: Usuario,

    /**
     * El centro donde imparte. Es lo que acota su acceso: un profesor solo llega
     * a alumnos, cursos y notas de su propio colegio.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_colegio", nullable = false)
    var colegio: Colegio,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profesor")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
