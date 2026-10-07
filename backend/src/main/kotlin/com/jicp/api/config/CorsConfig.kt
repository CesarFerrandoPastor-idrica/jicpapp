package com.jicp.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS para poder probar la app con `flutter run -d chrome`.
 *
 * Flutter web se sirve desde un puerto aleatorio de localhost, asi que el
 * navegador considera la API un origen distinto y bloquea las respuestas si
 * nadie le dice lo contrario.
 *
 * Solo se permiten origenes de localhost, y por patron de puerto en lugar de con
 * un comodin: un `*` abierto dejaria que cualquier web que visitase un alumno
 * lanzase peticiones a esta API desde su navegador. Los origenes reales de
 * produccion se anaden por la variable CORS_ORIGENES cuando haga falta; en
 * moviles CORS no interviene, porque no hay navegador de por medio.
 */
@Configuration
class CorsConfig(
    @Value("\${jicp.seguridad.cors.origenes:}") private val origenesExtra: String,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuracion = CorsConfiguration().apply {
            allowedOriginPatterns = ORIGENES_DE_DESARROLLO +
                origenesExtra.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            // La API es stateless y la credencial viaja en la cabecera Authorization,
            // no en cookies: no hay nada que justifique permitir credenciales.
            allowCredentials = false
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuracion)
        }
    }

    private companion object {
        val ORIGENES_DE_DESARROLLO = listOf(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
        )
    }
}
