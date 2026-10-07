package com.jicp.api.seguridad

import com.jicp.api.alumno.AlumnoResponse
import com.jicp.api.profesor.ProfesorResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "el email es obligatorio")
    @field:Email(message = "formato de email invalido")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val email: String,

    @field:NotBlank(message = "la contrasena es obligatoria")
    @field:Size(max = 72, message = "maximo 72 caracteres")
    val password: String,
)

data class RefrescarRequest(
    @field:NotBlank(message = "el refresh token es obligatorio")
    val refreshToken: String,
)

/**
 * El par de tokens. El access se manda en cada peticion; el refresh se guarda en
 * flutter_secure_storage y solo se usa contra /auth/refresh.
 */
data class TokensResponse(
    val accessToken: String,
    val refreshToken: String,
    /** Segundos de vida del access token, para que el cliente lo renueve antes de que caduque. */
    val expiraEn: Long,
    val rol: Rol,
    val idUsuario: Int,
)

/**
 * Respuesta de GET /yo.
 *
 * Solo viene relleno el campo que corresponde al rol: alumno o profesor, nunca los dos.
 * Se prefieren dos campos anulables a una respuesta plana que mezcle los de ambos, porque
 * jicpInicial no significa nada para un docente y el centro donde imparte no lo tiene un
 * alumno. El cliente mira el rol y lee el que toca.
 */
data class PerfilResponse(
    val idUsuario: Int,
    val email: String,
    val rol: Rol,
    val alumno: AlumnoResponse?,
    val profesor: ProfesorResponse?,
)
