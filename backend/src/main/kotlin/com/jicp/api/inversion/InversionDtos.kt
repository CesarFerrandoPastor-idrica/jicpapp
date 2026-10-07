package com.jicp.api.inversion

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Peticion de compra.
 *
 * El cliente manda una **intencion**, no un resultado: cuantas participaciones quiere y,
 * como mucho, a que precio creia que las compraba. Ni el importe ni el saldo resultante
 * viajan en la peticion; si vinieran, se ignorarian.
 */
data class InvertirRequest(
    @field:NotNull(message = "el proyecto es obligatorio")
    @field:Positive(message = "identificador de proyecto invalido")
    val idProyecto: Int,

    @field:NotNull(message = "las participaciones son obligatorias")
    @field:DecimalMin(value = "0.0001", message = "hay que comprar al menos 0.0001")
    @field:Digits(integer = 10, fraction = 4, message = "maximo 4 decimales")
    val participaciones: BigDecimal,

    /**
     * Precio que el alumno vio en pantalla. Es opcional, pero si viene y no coincide con
     * el actual la compra se rechaza con 409 en lugar de ejecutarse a un precio distinto
     * del que se le enseño. Con precio variable esto deja de ser un lujo.
     */
    @field:Digits(integer = 8, fraction = 4, message = "maximo 4 decimales")
    val precioUnitarioEsperado: BigDecimal? = null,
)

/** El recibo de la compra. El saldo que trae es el real, y es el que debe pintar la UI. */
data class InversionResponse(
    val idOperacion: Int,
    val idProyecto: Int,
    val nombreProyecto: String,
    val participacionesCompradas: BigDecimal,
    val precioUnitario: BigDecimal,
    val importe: BigDecimal,
    /** Total acumulado del alumno en este proyecto tras la compra. */
    val participacionesTotales: BigDecimal,
    val precioMedio: BigDecimal,
    /** Precio al que compraria el siguiente: ya ha subido por esta misma compra. */
    val precioSiguiente: BigDecimal,
    val saldoCartera: BigDecimal,
    val fecha: LocalDateTime,
)

/** Una posicion del portafolio. */
data class PosicionResponse(
    val idProyecto: Int,
    val nombreProyecto: String,
    val categoria: String,
    val estado: String,
    val participaciones: BigDecimal,
    val precioMedio: BigDecimal,
    val precioActual: BigDecimal,
    /** Lo que valdria hoy la posicion: participaciones x precio actual. */
    val valorActual: BigDecimal,
    /** Diferencia entre lo que vale y lo que costo. Negativa si pierde. */
    val plusvalia: BigDecimal,
)

/** Estado de mercado de un proyecto: lo que necesita la ficha para dejar invertir. */
data class MercadoProyectoResponse(
    val idProyecto: Int,
    val precioBase: BigDecimal,
    val precioActual: BigDecimal,
    val participacionesTotales: BigDecimal,
    val participacionesEmitidas: BigDecimal,
    val participacionesDisponibles: BigDecimal,
    val recaudado: BigDecimal,
    val inversores: Int,
)

/** Quien tiene participaciones de un proyecto. */
data class InversorResponse(
    val idAlumno: Int,
    val nombre: String,
    val apellido: String,
    val participaciones: BigDecimal,
)
