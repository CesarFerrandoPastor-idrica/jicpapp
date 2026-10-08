package com.jicp.api.seguridad

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Crea la primera cuenta de administrador de la instalacion.
 *
 * Las altas de colegios, alumnos y profesores son de `ADMIN`. De momento esa cuenta la
 * usan los desarrolladores; mas adelante, una persona de la empresa desde la web. Como
 * crear un admin exige ya ser admin, el primero no puede nacer por la API: lo crea quien
 * administra el servidor, con variables de entorno.
 *
 * ```
 * JICP_ADMIN_INICIAL_EMAIL=dev@jicp.example \
 * JICP_ADMIN_INICIAL_PASSWORD=... ./gradlew bootRun
 * ```
 *
 * Solo actua mientras no exista ningun admin, asi que no sirve para colar una cuenta en
 * un sistema ya en marcha. Y la contrasena no vive en el repositorio ni en ninguna
 * migracion: una credencial versionada es una credencial publica.
 */
@Component
class AdminInicial(
    private val usuarios: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${jicp.seguridad.admin-inicial.email:}") private val email: String,
    @Value("\${jicp.seguridad.admin-inicial.password:}") private val password: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank() || password.isBlank()) return

        if (usuarios.existsByRol(Rol.ADMIN)) {
            log.info("Ya hay un administrador: se ignora la configuracion de admin inicial.")
            return
        }

        val correo = email.trim().lowercase()
        if (usuarios.existsByEmail(correo)) {
            log.error("No se crea el admin inicial: el email {} ya esta registrado", correo)
            return
        }
        if (password.length < LONGITUD_MINIMA) {
            log.error("No se crea el admin inicial: la contrasena necesita al menos {} caracteres", LONGITUD_MINIMA)
            return
        }

        usuarios.save(
            Usuario(email = correo, passwordHash = passwordEncoder.encode(password), rol = Rol.ADMIN),
        )
        log.warn(
            "Creado el administrador inicial {}. Retira las variables JICP_ADMIN_INICIAL_* " +
                "del arranque.",
            correo,
        )
    }

    private companion object {
        /** La misma exigencia que las altas por la API. */
        const val LONGITUD_MINIMA = 8
    }
}
