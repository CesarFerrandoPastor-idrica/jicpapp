package com.jicp.api.proyecto

import com.jicp.api.alumno.AlumnoService
import com.jicp.api.alumno.Alumno
import com.jicp.api.contabilidad.ContabilidadService
import com.jicp.api.contabilidad.CuentaRepository
import com.jicp.api.contabilidad.OrdenTransferencia
import com.jicp.api.contabilidad.TipoOperacion
import com.jicp.api.inversion.Inversion
import com.jicp.api.inversion.InversionRepository
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.RecursoNoEncontradoException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProyectoService(
    private val repositorio: ProyectoRepository,
    private val categorias: CategoriaProyectoRepository,
    private val estados: EstadoProyectoRepository,
    private val roles: RolProyectoRepository,
    private val miembros: AlumnoProyectoRepository,
    private val alumnos: AlumnoService,
    private val contabilidad: ContabilidadService,
    private val cuentas: CuentaRepository,
    private val inversiones: InversionRepository,
) {

    fun listar(idColegio: Int?, idCategoria: Int?, idEstado: Int?, pageable: Pageable): Page<ProyectoResponse> {
        val pagina = repositorio.buscar(idColegio, idCategoria, idEstado, pageable)
        val creadores = creadoresDe(pagina.content.mapNotNull { it.id })
        return pagina.map { it.toResponse(creadores[it.id]) }
    }

    fun obtener(id: Int): ProyectoResponse {
        val proyecto = repositorio.buscarConRelaciones(id)
            ?: throw RecursoNoEncontradoException("proyecto", id)
        return proyecto.toResponse(creadoresDe(listOf(id))[id])
    }

    /**
     * Crea el proyecto y su vinculo de autoria en la misma transaccion: un proyecto
     * nunca llega a existir sin creador.
     *
     * El colegio se deriva del alumno creador en lugar de aceptarlo en la peticion,
     * para que no pueda darse de alta un proyecto en un centro distinto al de su autor.
     */
    @Transactional
    fun crear(peticion: CrearProyectoRequest, idAlumnoCreador: Int): ProyectoResponse {
        val creador = alumnos.buscarOFallar(idAlumnoCreador)

        val proyecto = repositorio.save(
            Proyecto(
                nombre = peticion.nombre.trim(),
                descripcion = peticion.descripcion.trim(),
                categoria = categoriaOFallar(peticion.idCategoria),
                estado = estadoOFallar(peticion.idEstado),
                colegio = creador.colegio,
                inversionInicial = peticion.inversionInicial,
                precioBase = peticion.precioBase,
                participacionesTotales = peticion.participacionesTotales,
            ),
        )

        val vinculo = miembros.save(
            AlumnoProyecto(alumno = creador, proyecto = proyecto, rol = rolOFallar(RolesProyecto.CREADOR)),
        )

        // La tesoreria existe desde el minuto cero: sin ella no habria donde ingresar la
        // inversion inicial ni, despues, el dinero de quien invierta.
        val tesoreria = contabilidad.abrirTesoreria(proyecto)

        if (peticion.inversionInicial > BigDecimal.ZERO) {
            aportarCapitalFundacional(creador, proyecto, tesoreria.id!!, peticion.inversionInicial)
        }

        return proyecto.toResponse(vinculo.toResumen())
    }

    /**
     * El fundador pone dinero de su cartera y recibe participaciones a cambio, al precio
     * base.
     *
     * Recibe participaciones en lugar de limitarse a financiar el proyecto porque asi la
     * tabla de capital cuadra: hay dinero en la tesoreria y alguien con derecho sobre el.
     * Ademas deja emitidas > 0 desde el principio, con lo que el siguiente inversor ya
     * paga por encima del precio base, que es justo lo que se quiere enseñar.
     */
    private fun aportarCapitalFundacional(
        creador: Alumno,
        proyecto: Proyecto,
        idTesoreria: Int,
        importe: BigDecimal,
    ) {
        val participaciones = importe.divide(proyecto.precioBase, 4, RoundingMode.HALF_UP)

        if (participaciones > proyecto.participacionesTotales) {
            throw ConflictoException(
                "Con una inversion inicial de $importe JICP a ${proyecto.precioBase} por " +
                    "participacion harian falta $participaciones, mas que las " +
                    "${proyecto.participacionesTotales} de la ronda. Sube la ronda o baja la inversion",
            )
        }

        val cartera = cuentas.carteraDe(creador.id!!)
            ?: throw IllegalStateException("El alumno ${creador.id} no tiene cartera")

        // Si el saldo no llega, esto lanza y la transaccion entera se deshace: el proyecto
        // no llega a existir. Fundar sin fondos no deja un proyecto a medias.
        contabilidad.ejecutar(
            OrdenTransferencia(
                tipo = TipoOperacion.CREACION_PROYECTO,
                actor = creador,
                idCuentaOrigen = cartera.id!!,
                idCuentaDestino = idTesoreria,
                importe = importe,
                claveIdempotencia = "creacion-proyecto-${proyecto.id}",
                proyecto = proyecto,
            ),
        )

        proyecto.participacionesEmitidas = participaciones
        inversiones.save(
            Inversion(
                proyecto = proyecto,
                alumno = creador,
                participaciones = participaciones,
                precioMedio = proyecto.precioBase,
            ),
        )
    }

    @Transactional
    fun actualizar(id: Int, peticion: ActualizarProyectoRequest): ProyectoResponse {
        val proyecto = buscarOFallar(id)

        proyecto.nombre = peticion.nombre.trim()
        proyecto.descripcion = peticion.descripcion.trim()
        proyecto.categoria = categoriaOFallar(peticion.idCategoria)
        proyecto.estado = estadoOFallar(peticion.idEstado)
        return proyecto.toResponse(creadoresDe(listOf(id))[id])
    }

    fun listarCategorias(): List<CatalogoResponse> =
        categorias.findAll().sortedBy { it.id }.map { it.toResponse() }

    fun listarEstados(): List<CatalogoResponse> =
        estados.findAll().sortedBy { it.id }.map { it.toResponse() }

    fun listarRoles(): List<CatalogoResponse> =
        roles.findAll().sortedBy { it.id }.map { it.toResponse() }

    fun buscarOFallar(id: Int): Proyecto =
        repositorio.findById(id).orElseThrow { RecursoNoEncontradoException("proyecto", id) }

    fun rolOFallar(id: Int): RolProyecto =
        roles.findById(id).orElseThrow { RecursoNoEncontradoException("rol de proyecto", id) }

    /** Creadores de varios proyectos en una sola consulta, para no provocar un N+1 en el listado. */
    fun creadoresDe(idsProyecto: List<Int>): Map<Int, MiembroResumen> =
        if (idsProyecto.isEmpty()) {
            emptyMap()
        } else {
            miembros.findByProyectoIdInAndRolId(idsProyecto, RolesProyecto.CREADOR)
                .associate { it.idProyecto to it.toResumen() }
        }

    private fun categoriaOFallar(id: Int): CategoriaProyecto =
        categorias.findById(id).orElseThrow { RecursoNoEncontradoException("categoria", id) }

    private fun estadoOFallar(id: Int): EstadoProyecto =
        estados.findById(id).orElseThrow { RecursoNoEncontradoException("estado", id) }
}
