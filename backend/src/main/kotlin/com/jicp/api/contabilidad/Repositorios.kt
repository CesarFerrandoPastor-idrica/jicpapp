package com.jicp.api.contabilidad

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface CuentaRepository : JpaRepository<Cuenta, Int> {

    /**
     * Bloqueo pesimista (`SELECT ... FOR UPDATE`) de varias cuentas de golpe.
     *
     * El `order by c.id` no es cosmetico: con dos cuentas en juego, bloquear "primero
     * la mia y luego la suya" produce un interbloqueo en cuanto dos transferencias
     * cruzadas coinciden. Ordenar siempre por id lo elimina.
     *
     * La contencion es despreciable: se bloquea la fila de una cuenta concreta, no la tabla.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id in :ids order by c.id")
    fun bloquear(@Param("ids") ids: List<Int>): List<Cuenta>

    @Query("select c from Cuenta c where c.alumno.id = :idAlumno and c.tipo = 'CARTERA_ALUMNO'")
    fun carteraDe(@Param("idAlumno") idAlumno: Int): Cuenta?

    @Query("select c from Cuenta c where c.proyecto.id = :idProyecto and c.tipo = 'TESORERIA_PROYECTO'")
    fun tesoreriaDe(@Param("idProyecto") idProyecto: Int): Cuenta?

    @Query("select c from Cuenta c where c.tipo = 'EMISION_SISTEMA'")
    fun emision(): Cuenta?
}

interface OperacionJicpRepository : JpaRepository<OperacionJicp, Int> {

    /**
     * Recupera una operacion ya ejecutada a partir de su clave. Es el atajo optimista
     * de la idempotencia: la garantia real la da el indice unico, no esta consulta.
     */
    @EntityGraph(attributePaths = ["actor", "proyecto"])
    fun findByActorIdAndClaveIdempotencia(
        idActor: Int,
        claveIdempotencia: String,
    ): OperacionJicp?
}

interface ApunteJicpRepository : JpaRepository<ApunteJicp, Long> {

    @EntityGraph(attributePaths = ["operacion", "operacion.proyecto"])
    fun findByCuentaIdOrderByIdDesc(idCuenta: Int, pageable: Pageable): Page<ApunteJicp>

    fun findByOperacionIdOrderByIdAsc(idOperacion: Int): List<ApunteJicp>

    // --- Consultas de reconciliacion: las tres deben salir vacias o a cero, siempre. ---

    /** Operaciones cuyos apuntes no suman cero: alguien ha creado o destruido dinero. */
    @Query(
        """
        select a.operacion.id from ApunteJicp a
        group by a.operacion.id
        having sum(a.importe) <> 0
        """,
    )
    fun operacionesDescuadradas(): List<Int>

    /** Cuentas cuyo saldo cacheado no coincide con la suma de sus apuntes. */
    @Query(
        """
        select c.id from Cuenta c
        where c.saldo <> coalesce(
            (select sum(a.importe) from ApunteJicp a where a.cuenta.id = c.id), 0
        )
        """,
    )
    fun cuentasDescuadradas(): List<Int>

    /** Masa monetaria global. En una economia cerrada tiene que ser exactamente 0. */
    @Query("select coalesce(sum(a.importe), 0) from ApunteJicp a")
    fun masaMonetaria(): BigDecimal
}
