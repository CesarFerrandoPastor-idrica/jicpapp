package com.jicp.api.seguridad

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Parametros de firma y caducidad. Se configuran en application.yml bajo `jicp.seguridad.jwt`
 * y el secreto llega por variable de entorno.
 */
@ConfigurationProperties(prefix = "jicp.seguridad.jwt")
data class PropiedadesJwt(

    /** Clave HMAC. Minimo 32 bytes: HS256 no admite claves mas cortas. */
    val secreto: String,

    /**
     * Corta a proposito. Un access token no se puede revocar antes de que expire,
     * asi que la ventana de dano de uno robado es exactamente esta duracion.
     */
    val duracionAccess: Duration = Duration.ofMinutes(15),

    /** Larga, porque si se compromete si se puede revocar: vive en base de datos. */
    val duracionRefresh: Duration = Duration.ofDays(30),
)
