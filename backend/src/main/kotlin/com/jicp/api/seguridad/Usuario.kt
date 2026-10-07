package com.jicp.api.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/** Quien puede entrar y con que permisos. Se guarda como texto, no como ordinal: */
enum class Rol {
    ALUMNO,
    PROFESOR,
    ADMIN,
}

/**
 * Credenciales y rol. Es el unico sitio del esquema donde vive un email y una contrasena.
 *
 * Alumno y (mas adelante) profesor cuelgan de aqui en lugar de repetir los mismos campos:
 * asi el login es una sola consulta, el email es unico en todo el sistema y el `sub` del
 * JWT identifica a la persona con independencia de su rol.
 */
@Entity
@Table(name = "usuario")
class Usuario(

    @Column(name = "email", nullable = false, length = 255, unique = true)
    var email: String,

    /** Hash BCrypt, nunca la contrasena en claro. */
    @Column(name = "password", nullable = false, length = 255)
    var passwordHash: String,

    /**
     * EnumType.STRING y no ORDINAL: con ordinales, insertar un rol nuevo en medio del enum
     * reasignaria en silencio los permisos de todas las filas ya guardadas.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    var rol: Rol,

    /** Una baja desactiva el acceso; no se borra la fila porque de ella cuelga el historial. */
    @Column(name = "activo", nullable = false)
    var activo: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
