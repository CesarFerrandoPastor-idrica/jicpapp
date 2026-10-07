package com.jicp.api.proyecto

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CategoriaProyectoRepository : JpaRepository<CategoriaProyecto, Int> {
    fun findByNombreIgnoreCase(nombre: String): CategoriaProyecto?
}

interface EstadoProyectoRepository : JpaRepository<EstadoProyecto, Int> {
    fun findByNombreIgnoreCase(nombre: String): EstadoProyecto?
}

interface RolProyectoRepository : JpaRepository<RolProyecto, Int>

interface AlumnoProyectoRepository : JpaRepository<AlumnoProyecto, AlumnoProyectoId> {

    // alumno.usuario entra en el grafo porque la respuesta de miembro expone su email.
    @EntityGraph(attributePaths = ["alumno", "alumno.usuario", "rol"])
    fun findByProyectoId(idProyecto: Int): List<AlumnoProyecto>

    @EntityGraph(attributePaths = ["proyecto", "rol"])
    fun findByAlumnoId(idAlumno: Int): List<AlumnoProyecto>

    @EntityGraph(attributePaths = ["alumno", "alumno.usuario", "rol"])
    fun findByProyectoIdAndAlumnoId(idProyecto: Int, idAlumno: Int): AlumnoProyecto?

    /**
     * Creadores de varios proyectos de una sola consulta: evita el N+1 al montar
     * el listado del mercado, donde cada tarjeta muestra quien fundó el proyecto.
     */
    @EntityGraph(attributePaths = ["alumno"])
    fun findByProyectoIdInAndRolId(idsProyecto: Collection<Int>, idRol: Int): List<AlumnoProyecto>

    fun countByProyectoId(idProyecto: Int): Long
}

interface ProyectoRepository : JpaRepository<Proyecto, Int> {

    /**
     * Listado del mercado con filtros opcionales.
     *
     * Los `join fetch` evitan el N+1 al construir la respuesta (categoria, estado y colegio
     * se traen en la misma consulta). Al ser asociaciones ToOne, la paginacion sigue
     * resolviendose en SQL y no en memoria.
     */
    @Query(
        value = """
            select p from Proyecto p
            join fetch p.categoria
            join fetch p.estado
            join fetch p.colegio
            where (:idColegio is null or p.colegio.id = :idColegio)
              and (:idCategoria is null or p.categoria.id = :idCategoria)
              and (:idEstado is null or p.estado.id = :idEstado)
        """,
        countQuery = """
            select count(p) from Proyecto p
            where (:idColegio is null or p.colegio.id = :idColegio)
              and (:idCategoria is null or p.categoria.id = :idCategoria)
              and (:idEstado is null or p.estado.id = :idEstado)
        """,
    )
    fun buscar(
        @Param("idColegio") idColegio: Int?,
        @Param("idCategoria") idCategoria: Int?,
        @Param("idEstado") idEstado: Int?,
        pageable: Pageable,
    ): Page<Proyecto>

    @Query(
        """
        select p from Proyecto p
        join fetch p.categoria
        join fetch p.estado
        join fetch p.colegio
        where p.id = :id
        """,
    )
    fun buscarConRelaciones(@Param("id") id: Int): Proyecto?

    /**
     * Bloqueo pesimista de la fila del proyecto para operar en su mercado.
     *
     * Hace falta porque el precio depende de `participaciones_emitidas` y esa columna se
     * muta en la misma operacion: sin el, dos compras simultaneas leerian las mismas
     * emitidas, pagarian ambas el precio viejo y podrian pasarse del tamano de la ronda.
     *
     * Se toma SIEMPRE antes que los bloqueos de cuenta, para que el orden sea el mismo
     * en todos los caminos y no puedan cruzarse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proyecto p where p.id = :id")
    fun bloquear(@Param("id") id: Int): Proyecto?
}
