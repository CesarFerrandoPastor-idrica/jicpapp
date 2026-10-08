package com.jicp.api.seguridad

import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.alumno.toResponse
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.profesor.toResponse
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.CredencialesInvalidasException
import com.jicp.api.shared.SinPermisoException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
@Transactional(readOnly = true)
class ServicioAutenticacion(
    private val usuarios: UsuarioRepository,
    private val refrescos: RefreshTokenRepository,
    private val alumnos: AlumnoRepository,
    private val profesores: ProfesorRepository,
    private val jwt: ServicioJwt,
    private val passwordEncoder: PasswordEncoder,
    private val propiedades: PropiedadesJwt,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val aleatorio = SecureRandom()

    /**
     * Hash de una contrasena que no es la de nadie. Se compara contra el cuando el email no
     * existe, para que la respuesta tarde lo mismo que con un email real: sin esto, el tiempo
     * de respuesta seria un oraculo de que cuentas estan dadas de alta.
     */
    private val hashSenuelo: String = passwordEncoder.encode("senuelo-contra-la-medicion-de-tiempos")

    @Transactional
    fun login(peticion: LoginRequest): TokensResponse {
        val email = peticion.email.trim().lowercase()
        val usuario = usuarios.findByEmail(email)

        val contrasenaCorrecta = passwordEncoder.matches(
            peticion.password,
            usuario?.passwordHash ?: hashSenuelo,
        )

        // Un unico error para las tres causas (email desconocido, contrasena incorrecta y
        // cuenta desactivada): distinguirlas le diria a quien prueba credenciales cual de
        // los dos campos ha acertado.
        if (usuario == null || !contrasenaCorrecta || !usuario.activo) {
            log.debug("Login rechazado para {}", email)
            throw CredencialesInvalidasException()
        }

        return emitir(usuario)
    }

    /**
     * Rotacion: cada renovacion quema el token presentado y devuelve uno nuevo.
     *
     * noRollbackFor es imprescindible por la deteccion de reutilizacion: ahi se revoca la
     * sesion y acto seguido se lanza la excepcion, y sin esto el rollback desharia justo
     * la revocacion que se acaba de hacer.
     */
    @Transactional(noRollbackFor = [CredencialesInvalidasException::class])
    fun refrescar(peticion: RefrescarRequest): TokensResponse {
        val ahora = LocalDateTime.now()
        val token = refrescos.findByHashToken(hashear(peticion.refreshToken))
            ?: throw CredencialesInvalidasException()

        if (token.fechaRevocacion != null) {
            // Alguien esta usando un token ya rotado. O lo han robado, o el cliente se quedo
            // con una copia vieja. Como no hay forma de saber cual de los dos es el legitimo,
            // se cierran todas las sesiones del usuario y que vuelva a entrar.
            val cortadas = refrescos.revocarSesionesDe(requireNotNull(token.usuario.id), ahora)
            log.warn(
                "Refresh token reutilizado por el usuario {}: revocadas {} sesiones",
                token.usuario.id,
                cortadas,
            )
            throw CredencialesInvalidasException()
        }

        if (!token.estaVivo(ahora) || !token.usuario.activo) {
            throw CredencialesInvalidasException()
        }

        return emitir(token.usuario, anterior = token)
    }

    /** Cierre de sesion: revoca el refresh token. El access sigue valido hasta que caduque. */
    @Transactional
    fun logout(peticion: RefrescarRequest) {
        val token = refrescos.findByHashToken(hashear(peticion.refreshToken)) ?: return
        if (token.fechaRevocacion == null) {
            token.fechaRevocacion = LocalDateTime.now()
        }
    }

    /**
     * Cambia el email y/o la contrasena del usuario del token.
     *
     * - La contrasena actual tiene que ser correcta (si no, `403`; no `401`, que la app
     *   interpretaria como sesion caducada e intentaria renovar el token).
     * - El email sigue siendo unico en todo el sistema (`409` si ya lo usa otro).
     * - Si cambia la contrasena, se revocan **todas** sus sesiones: si alguien se la
     *   habia robado, la pierde. La app vuelve a entrar con la nueva.
     */
    @Transactional
    fun actualizarMiCuenta(autenticado: UsuarioAutenticado, peticion: ActualizarMiCuentaRequest): PerfilResponse {
        val usuario = usuarios.findById(autenticado.idUsuario)
            .orElseThrow { CredencialesInvalidasException() }

        if (!passwordEncoder.matches(peticion.passwordActual, usuario.passwordHash)) {
            throw SinPermisoException("La contrasena actual no es correcta")
        }

        val emailNuevo = peticion.email?.trim()?.lowercase()?.ifBlank { null }
        val passwordNueva = peticion.passwordNueva?.ifBlank { null }
        if ((emailNuevo == null || emailNuevo == usuario.email) && passwordNueva == null) {
            throw ConflictoException("No hay nada que cambiar")
        }

        if (emailNuevo != null && emailNuevo != usuario.email) {
            if (usuarios.existsByEmail(emailNuevo)) {
                throw ConflictoException("Ya existe un usuario registrado con el email $emailNuevo")
            }
            usuario.email = emailNuevo
        }

        if (passwordNueva != null) {
            usuario.passwordHash = passwordEncoder.encode(passwordNueva)
            refrescos.revocarSesionesDe(requireNotNull(usuario.id), LocalDateTime.now())
        }

        return perfil(autenticado)
    }

    fun perfil(autenticado: UsuarioAutenticado): PerfilResponse {
        val usuario = usuarios.findById(autenticado.idUsuario)
            .orElseThrow { CredencialesInvalidasException() }
        val idUsuario = requireNotNull(usuario.id) { "Usuario sin persistir" }

        // Se consulta solo la tabla del rol que toca: un ADMIN no tiene ninguna de las dos
        // y no hay razon para lanzar dos consultas que van a salir vacias.
        return PerfilResponse(
            idUsuario = idUsuario,
            email = usuario.email,
            rol = usuario.rol,
            alumno = if (usuario.rol == Rol.ALUMNO) {
                alumnos.findByUsuarioId(idUsuario)?.toResponse()
            } else {
                null
            },
            profesor = if (usuario.rol == Rol.PROFESOR) {
                profesores.findByUsuarioId(idUsuario)?.toResponse()
            } else {
                null
            },
        )
    }

    private fun emitir(usuario: Usuario, anterior: RefreshToken? = null): TokensResponse {
        val ahora = LocalDateTime.now()
        val valorDeRefresco = generarValorDeRefresco()

        val nuevo = refrescos.save(
            RefreshToken(
                usuario = usuario,
                hashToken = hashear(valorDeRefresco),
                fechaExpiracion = ahora.plus(propiedades.duracionRefresh),
            ),
        )

        anterior?.let {
            it.fechaRevocacion = ahora
            it.sustituto = nuevo
        }

        return TokensResponse(
            accessToken = jwt.generarAccessToken(usuario),
            refreshToken = valorDeRefresco,
            expiraEn = propiedades.duracionAccess.toSeconds(),
            rol = usuario.rol,
            idUsuario = requireNotNull(usuario.id) { "Usuario sin persistir" },
        )
    }

    /** 256 bits de SecureRandom en Base64 url-safe: no es adivinable ni necesita ser legible. */
    private fun generarValorDeRefresco(): String {
        val bytes = ByteArray(32)
        aleatorio.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256 en hexadecimal. No lleva BCrypt a proposito: el valor ya son 256 bits
     * aleatorios, asi que no hay nada que ralentizar frente a fuerza bruta, y un hash
     * determinista permite localizarlo por indice en una sola consulta.
     */
    private fun hashear(valor: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(valor.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
