package com.jicp.api.colegio

import com.jicp.api.shared.RecursoNoEncontradoException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ColegioService(
    private val repositorio: ColegioRepository,
) {

    fun listar(nombre: String?, pageable: Pageable): Page<ColegioResponse> {
        val pagina = if (nombre.isNullOrBlank()) {
            repositorio.findAll(pageable)
        } else {
            repositorio.findByNombreContainingIgnoreCase(nombre, pageable)
        }
        return pagina.map { it.toResponse() }
    }

    fun obtener(id: Int): ColegioResponse = buscarOFallar(id).toResponse()

    @Transactional
    fun crear(peticion: CrearColegioRequest): ColegioResponse {
        val colegio = Colegio(
            nombre = peticion.nombre.trim(),
            direccion = peticion.direccion.trim(),
            email = peticion.email.trim().lowercase(),
        )
        return repositorio.save(colegio).toResponse()
    }

    @Transactional
    fun actualizar(id: Int, peticion: ActualizarColegioRequest): ColegioResponse {
        val colegio = buscarOFallar(id)
        colegio.nombre = peticion.nombre.trim()
        colegio.direccion = peticion.direccion.trim()
        colegio.email = peticion.email.trim().lowercase()
        return colegio.toResponse()
    }

    /** Uso interno de otros servicios que necesitan la entidad, no el DTO. */
    fun buscarOFallar(id: Int): Colegio =
        repositorio.findById(id).orElseThrow { RecursoNoEncontradoException("colegio", id) }
}
