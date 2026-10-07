package com.jicp.api.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lee la cabecera `Authorization: Bearer <jwt>` y, si el token es valido, deja al usuario
 * en el contexto de seguridad de la peticion.
 *
 * Un token ausente o invalido no corta la peticion aqui: simplemente la deja anonima y es
 * la configuracion de rutas la que decide si eso basta. Asi los endpoints todavia abiertos
 * siguen funcionando mientras la app Flutter termina de migrar.
 */
@Component
class FiltroJwt(private val jwt: ServicioJwt) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cabecera = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (cabecera != null &&
            cabecera.startsWith(PREFIJO_BEARER) &&
            SecurityContextHolder.getContext().authentication == null
        ) {
            jwt.leer(cabecera.substring(PREFIJO_BEARER.length).trim())?.let { usuario ->
                val autenticacion = UsernamePasswordAuthenticationToken(
                    usuario,
                    null,
                    usuario.autorizaciones,
                )
                autenticacion.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = autenticacion
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val PREFIJO_BEARER = "Bearer "
    }
}
