package com.jicp.api.profesor

import com.jicp.api.colegio.ColegioRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import com.jicp.api.seguridad.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Crea el primer profesor de la instalacion.
 *
 * Si a un profesor solo lo puede dar de alta otro profesor, el primero no puede
 * nacer por la API: es la pescadilla que se muerde la cola de todo sistema de
 * permisos. La salida habitual —dejar el endpoint de alta abierto "solo al
 * principio"— deja una puerta que nadie se acuerda de cerrar despues.
 *
 * Aqui la puerta la abre quien administra el servidor, no quien llega por red:
 * hace falta poder poner variables de entorno en la maquina. Y solo funciona
 * mientras no exista ningun profesor, asi que no sirve para colar una cuenta en
 * un sistema ya en marcha.
 *
 * ```
 * JICP_PROFESOR_INICIAL_EMAIL=jefatura@ies.example \
 * JICP_PROFESOR_INICIAL_PASSWORD=... \
 * JICP_PROFESOR_INICIAL_ID_COLEGIO=1 ./gradlew bootRun
 * ```
 *
 * La contrasena no vive en el repositorio ni en ninguna migracion a proposito:
 * una credencial versionada es una credencial publica.
 */
@Component
class ProfesorInicial(
    private val profesores: ProfesorRepository,
    private val colegios: ColegioRepository,
    private val usuarios: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${jicp.seguridad.profesor-inicial.email:}") private val email: String,
    @Value("\${jicp.seguridad.profesor-inicial.password:}") private val password: String,
    @Value("\${jicp.seguridad.profesor-inicial.id-colegio:0}") private val idColegio: Int,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank() || password.isBlank()) return

        if (profesores.count() > 0) {
            log.info(
                "Hay profesorado dado de alta: se ignora la configuracion de profesor inicial. " +
                    "Las altas nuevas van por POST /api/v1/profesores.",
            )
            return
        }

        val correo = email.trim().lowercase()
        if (usuarios.existsByEmail(correo)) {
            log.error("No se crea el profesor inicial: el email {} ya esta registrado", correo)
            return
        }

        val colegio = colegios.findById(idColegio).orElse(null)
        if (colegio == null) {
            log.error(
                "No se crea el profesor inicial: no existe el colegio {}. " +
                    "Da de alta el centro primero y define JICP_PROFESOR_INICIAL_ID_COLEGIO.",
                idColegio,
            )
            return
        }

        profesores.save(
            Profesor(
                nombre = "Profesor",
                apellido = "Inicial",
                usuario = Usuario(
                    email = correo,
                    passwordHash = passwordEncoder.encode(password),
                    rol = Rol.PROFESOR,
                ),
                colegio = colegio,
            ),
        )
        log.warn(
            "Creado el profesor inicial {} en el centro '{}'. Cambia su contrasena y retira " +
                "las variables JICP_PROFESOR_INICIAL_* del arranque.",
            correo,
            colegio.nombre,
        )
    }
}
