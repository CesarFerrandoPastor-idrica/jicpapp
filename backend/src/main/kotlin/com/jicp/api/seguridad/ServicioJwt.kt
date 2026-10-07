package com.jicp.api.seguridad

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * Emite y verifica los access token. El token lleva lo justo para autorizar sin
 * volver a la base de datos: quien es (`sub`) y con que rol actua.
 *
 * No lleva saldo, ni nombre, ni nada que pueda quedarse obsoleto: el cliente no debe
 * poder leer del token nada que el servidor considere autoritativo.
 */
@Service
class ServicioJwt(private val propiedades: PropiedadesJwt) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val clave = run {
        val bytes = propiedades.secreto.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= 32) {
            "jicp.seguridad.jwt.secreto necesita al menos 32 bytes para firmar con HS256"
        }
        if (propiedades.secreto == SECRETO_DE_DESARROLLO) {
            log.warn(
                "Arrancando con el secreto JWT de desarrollo, que esta en el repositorio. " +
                    "Define JWT_SECRET antes de exponer esta API a cualquier red.",
            )
        }
        io.jsonwebtoken.security.Keys.hmacShaKeyFor(bytes)
    }

    fun generarAccessToken(usuario: Usuario): String {
        val ahora = Date()
        return Jwts.builder()
            .issuer(EMISOR)
            .subject(requireNotNull(usuario.id) { "Usuario sin persistir" }.toString())
            .claim("rol", usuario.rol.name)
            .claim("email", usuario.email)
            .issuedAt(ahora)
            .expiration(Date(ahora.time + propiedades.duracionAccess.toMillis()))
            .signWith(clave)
            .compact()
    }

    /**
     * Devuelve null ante cualquier token que no sea valido: firma incorrecta, caducado,
     * emisor ajeno o simplemente basura. El motivo no sale al cliente a proposito.
     */
    fun leer(token: String): UsuarioAutenticado? = try {
        val claims = Jwts.parser()
            .verifyWith(clave)
            .requireIssuer(EMISOR)
            .build()
            .parseSignedClaims(token)
            .payload

        UsuarioAutenticado(
            idUsuario = claims.subject.toInt(),
            email = claims["email"] as String,
            rol = Rol.valueOf(claims["rol"] as String),
        )
    } catch (e: JwtException) {
        log.debug("Token JWT rechazado: {}", e.message)
        null
    } catch (e: IllegalArgumentException) {
        log.debug("Token JWT con contenido inesperado: {}", e.message)
        null
    }

    companion object {
        const val EMISOR = "jicp-api"

        /** Debe coincidir con el valor por defecto de application.yml. */
        const val SECRETO_DE_DESARROLLO = "desarrollo-local-jicp-secreto-de-firma-cambiame-en-produccion"
    }
}
