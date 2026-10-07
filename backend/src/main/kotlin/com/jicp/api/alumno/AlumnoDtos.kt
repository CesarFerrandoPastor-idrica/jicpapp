package com.jicp.api.alumno

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CrearAlumnoRequest(
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

    @field:NotNull(message = "el colegio es obligatorio")
    @field:Positive(message = "identificador de colegio invalido")
    val idColegio: Int,

    /**
     * Concesion inicial de JICP. Solo deberia poder fijarla un profesor o un admin:
     * en cuanto exista autenticacion, este campo se ignora para los alumnos.
     */
    @field:DecimalMin(value = "0.00", message = "no puede ser negativa")
    @field:Digits(integer = 10, fraction = 2, message = "maximo 10 enteros y 2 decimales")
    val jicpInicial: BigDecimal = BigDecimal.ZERO,
)

data class ActualizarAlumnoRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 100, message = "maximo 100 caracteres")
    val nombre: String,

    @field:NotBlank(message = "el apellido es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val apellido: String,
)

/** Respuesta publica de un alumno. Nunca incluye la contrasena ni su hash. */
data class AlumnoResponse(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val email: String,
    val idColegio: Int,
    val nombreColegio: String,
    val jicpInicial: BigDecimal,
    val fechaRegistro: LocalDateTime,
)

fun Alumno.toResponse() = AlumnoResponse(
    id = requireNotNull(id) { "Alumno sin persistir" },
    nombre = nombre,
    apellido = apellido,
    email = usuario.email,
    idColegio = requireNotNull(colegio.id) { "Colegio sin persistir" },
    nombreColegio = colegio.nombre,
    jicpInicial = jicpInicial,
    fechaRegistro = fechaRegistro,
)
