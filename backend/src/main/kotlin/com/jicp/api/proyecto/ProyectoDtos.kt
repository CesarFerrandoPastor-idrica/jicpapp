package com.jicp.api.proyecto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CrearProyectoRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val nombre: String,

    @field:NotBlank(message = "la descripcion es obligatoria")
    val descripcion: String,

    @field:NotNull(message = "la categoria es obligatoria")
    @field:Positive(message = "identificador de categoria invalido")
    val idCategoria: Int,

    @field:NotNull(message = "el estado es obligatorio")
    @field:Positive(message = "identificador de estado invalido")
    val idEstado: Int,

    // El alumno creador NO se envia: sale del token. Aceptarlo aqui permitiria fundar un
    // proyecto a nombre de otro y, ahora que el alta descuenta dinero, gastarle el saldo.

    /**
     * Lo que el fundador pone de su cartera. Se le descuenta y se abona en la tesoreria
     * del proyecto dentro de la misma transaccion, y le da participaciones al precio base.
     */
    @field:DecimalMin(value = "0.00", message = "no puede ser negativa")
    @field:Digits(integer = 10, fraction = 2, message = "maximo 10 enteros y 2 decimales")
    val inversionInicial: BigDecimal = BigDecimal.ZERO,

    /** Precio de la primera participacion. A partir de ahi sube segun la demanda. */
    @field:DecimalMin(value = "0.01", message = "el precio base debe ser mayor que cero")
    @field:Digits(integer = 10, fraction = 2, message = "maximo 10 enteros y 2 decimales")
    val precioBase: BigDecimal = BigDecimal("100.00"),

    /** Tamano de la ronda: cuantas participaciones se pueden emitir como maximo. */
    @field:DecimalMin(value = "0.0001", message = "la ronda debe tener participaciones")
    @field:Digits(integer = 10, fraction = 4, message = "maximo 4 decimales")
    val participacionesTotales: BigDecimal = BigDecimal("100.0000"),
)

data class ActualizarProyectoRequest(
    @field:NotBlank(message = "el nombre es obligatorio")
    @field:Size(max = 150, message = "maximo 150 caracteres")
    val nombre: String,

    @field:NotBlank(message = "la descripcion es obligatoria")
    val descripcion: String,

    @field:NotNull(message = "la categoria es obligatoria")
    @field:Positive(message = "identificador de categoria invalido")
    val idCategoria: Int,

    @field:NotNull(message = "el estado es obligatorio")
    @field:Positive(message = "identificador de estado invalido")
    val idEstado: Int,
)

data class AnadirMiembroRequest(
    @field:NotNull(message = "el alumno es obligatorio")
    @field:Positive(message = "identificador de alumno invalido")
    val idAlumno: Int,

    @field:NotNull(message = "el rol es obligatorio")
    @field:Positive(message = "identificador de rol invalido")
    val idRol: Int,
)

data class CambiarRolRequest(
    @field:NotNull(message = "el rol es obligatorio")
    @field:Positive(message = "identificador de rol invalido")
    val idRol: Int,
)

data class ProyectoResponse(
    val id: Int,
    val nombre: String,
    val descripcion: String,
    val categoria: CatalogoResponse,
    val estado: CatalogoResponse,
    val idColegio: Int,
    val nombreColegio: String,
    val inversionInicial: BigDecimal,

    // Datos de mercado: la app los necesita para confirmar el alta sin tener que
    // encadenar una segunda llamada a /mercado justo despues de crear.
    val precioBase: BigDecimal,
    val participacionesTotales: BigDecimal,
    val participacionesEmitidas: BigDecimal,

    val creador: MiembroResumen?,
    val fechaRegistro: LocalDateTime,
)

/** Datos minimos de un alumno dentro de un proyecto. */
data class MiembroResumen(
    val idAlumno: Int,
    val nombre: String,
    val apellido: String,
)

data class MiembroProyectoResponse(
    val idAlumno: Int,
    val nombre: String,
    val apellido: String,
    val email: String,
    val rol: CatalogoResponse,
)

/** Un proyecto en el que participa un alumno, junto al papel que desempeña en el. */
data class ProyectoConRolResponse(
    val rol: CatalogoResponse,
    val proyecto: ProyectoResponse,
)

/** Forma comun de los catalogos (categoria, estado y rol). */
data class CatalogoResponse(
    val id: Int,
    val nombre: String,
    val descripcion: String?,
)

fun CategoriaProyecto.toResponse() = CatalogoResponse(
    id = requireNotNull(id) { "Categoria sin persistir" },
    nombre = nombre,
    descripcion = descripcion,
)

fun EstadoProyecto.toResponse() = CatalogoResponse(
    id = requireNotNull(id) { "Estado sin persistir" },
    nombre = nombre,
    descripcion = descripcion,
)

fun RolProyecto.toResponse() = CatalogoResponse(
    id = requireNotNull(id) { "Rol sin persistir" },
    nombre = nombre,
    descripcion = descripcion,
)

fun AlumnoProyecto.toMiembroResponse() = MiembroProyectoResponse(
    idAlumno = requireNotNull(alumno.id) { "Alumno sin persistir" },
    nombre = alumno.nombre,
    apellido = alumno.apellido,
    email = alumno.usuario.email,
    rol = rol.toResponse(),
)

fun AlumnoProyecto.toResumen() = MiembroResumen(
    idAlumno = requireNotNull(alumno.id) { "Alumno sin persistir" },
    nombre = alumno.nombre,
    apellido = alumno.apellido,
)

/**
 * El creador se pasa explicitamente en lugar de navegarlo desde la entidad:
 * asi el listado del mercado puede resolverlo en una sola consulta para toda la pagina
 * y no queda ninguna llamada que se olvide de incluirlo.
 */
fun Proyecto.toResponse(creador: MiembroResumen?) = ProyectoResponse(
    id = requireNotNull(id) { "Proyecto sin persistir" },
    nombre = nombre,
    descripcion = descripcion,
    categoria = categoria.toResponse(),
    estado = estado.toResponse(),
    idColegio = requireNotNull(colegio.id) { "Colegio sin persistir" },
    nombreColegio = colegio.nombre,
    inversionInicial = inversionInicial,
    precioBase = precioBase,
    participacionesTotales = participacionesTotales,
    participacionesEmitidas = participacionesEmitidas,
    creador = creador,
    fechaRegistro = fechaRegistro,
)
