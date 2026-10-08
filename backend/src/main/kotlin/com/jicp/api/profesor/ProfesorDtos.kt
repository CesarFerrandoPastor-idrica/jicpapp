package com.jicp.api.profesor

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CrearProfesorRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 100, message = "maximo 100 caracteres")
    val nombre: String,

    @field:NotBlank(message = "el apellido es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val apellido: String,

    @field:NotBlank(message = "el email es obligatorio")
    @field:Email(message = "formato de email invalido")
    @field:Size(max = 255, message = "maximo 255 caracteres")
    val email: String,

    @field:NotBlank(message = "la contrasena es obligatoria")
    @field:Size(min = 8, max = 72, message = "entre 8 y 72 caracteres")
    val password: String,

    /**
     * El centro donde imparte. Viene en la peticion porque las altas son de ADMIN, que no
     * pertenece a ningun centro. Mientras daba de alta otro profesor se heredaba del suyo;
     * si eso vuelve, este campo tendra que ignorarse para el profesorado.
     */
    @field:NotNull(message = "el colegio es obligatorio")
    @field:Positive(message = "identificador de colegio invalido")
    val idColegio: Int,
)

data class ActualizarProfesorRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 100, message = "maximo 100 caracteres")
    val nombre: String,

    @field:NotBlank(message = "el apellido es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val apellido: String,
)

/** Respuesta publica de un profesor. Nunca incluye la contrasena ni su hash. */
data class ProfesorResponse(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val email: String,
    val idColegio: Int,
    val nombreColegio: String,
    val fechaRegistro: LocalDateTime,
)

fun Profesor.toResponse() = ProfesorResponse(
    id = requireNotNull(id) { "Profesor sin persistir" },
    nombre = nombre,
    apellido = apellido,
    email = usuario.email,
    idColegio = requireNotNull(colegio.id) { "Colegio sin persistir" },
    nombreColegio = colegio.nombre,
    fechaRegistro = fechaRegistro,
)
