package com.jicp.api.seguridad

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Los rechazos de Spring Security ocurren en la cadena de filtros, antes de que exista
 * un controlador, asi que el @RestControllerAdvice no llega a verlos. Sin estas dos clases
 * el cliente recibiria la pagina de error HTML por defecto en vez de problem+json.
 */

/** Falta token o no es valido. */
@Component
class PuntoDeEntradaNoAutenticado(private val json: ObjectMapper) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = escribirProblema(
        response,
        json,
        HttpStatus.UNAUTHORIZED,
        "No autenticado",
        "Esta operacion requiere un token de acceso valido",
        "no-autenticado",
    )
}

/** Hay token valido, pero el rol no alcanza para el recurso. */
@Component
class ManejadorDeAccesoDenegado(private val json: ObjectMapper) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = escribirProblema(
        response,
        json,
        HttpStatus.FORBIDDEN,
        "Acceso denegado",
        "Tu rol no permite realizar esta operacion",
        "acceso-denegado",
    )
}

private fun escribirProblema(
    response: HttpServletResponse,
    json: ObjectMapper,
    status: HttpStatus,
    titulo: String,
    detalle: String,
    tipo: String,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()

    val problema = ProblemDetail.forStatusAndDetail(status, detalle).apply {
        title = titulo
        type = URI.create("https://jicp.app/errores/$tipo")
    }
    json.writeValue(response.outputStream, problema)
}
