package com.jicp.api.colegio

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CrearColegioRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val nombre: String,

    @field:NotBlank(message = "la direccion es obligatoria")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val direccion: String,

    @field:NotBlank(message = "el email es obligatorio")
    @field:Email(message = "formato de email invalido")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val email: String,
)

data class ActualizarColegioRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val nombre: String,

    @field:NotBlank(message = "la direccion es obligatoria")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val direccion: String,

    @field:NotBlank(message = "el email es obligatorio")
    @field:Email(message = "formato de email invalido")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val email: String,
)

data class ColegioResponse(
    val id: Int,
    val nombre: String,
    val direccion: String,
    val email: String,
    val fechaRegistro: LocalDateTime,
)

fun Colegio.toResponse() = ColegioResponse(
    id = requireNotNull(id) { "Colegio sin persistir" },
    nombre = nombre,
    direccion = direccion,
    email = email,
    fechaRegistro = fechaRegistro,
)
