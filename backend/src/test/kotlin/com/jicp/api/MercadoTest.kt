package com.jicp.api

import com.jicp.api.contabilidad.ApunteJicpRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El mercado de participaciones: crear proyecto, invertir y comentar.
 *
 * Incluye el test de concurrencia que exige el README, que es el unico que demuestra de
 * verdad que el bloqueo pesimista y el CHECK de saldo hacen su trabajo.
 */
class MercadoTest : PruebaDeIntegracion() {

    @Autowired private lateinit var apuntes: ApunteJicpRepository

    @Test
    fun `crear un proyecto descuenta la inversion inicial y abre su tesoreria`() {
        val colegio = crearColegio("IES Mercado", "mercado@ies.example")
        val fundador = crearAlumno(colegio, "fundador@ies.example", saldo = "10000.00")

        // Antes de fundar nada, el saldo es exactamente la concesion inicial.
        igual("10000.00", saldoDe(fundador), "saldo inicial")

        val proyecto = crearProyecto(
            fundador,
            nombre = "Eco-Drone Delivery",
            inversionInicial = "1200.00",
            precioBase = "150.00",
            participacionesTotales = "100.0000",
        )

        // 1200 de menos en la cartera del fundador...
        igual("8800.00", saldoDe(fundador), "saldo tras fundar")

        // ...y 1200 de mas en la tesoreria del proyecto, con 8 participaciones emitidas
        // (1200 / 150) que ya han movido el precio.
        val mercado = mercadoDe(proyecto)
        igual("1200.00", mercado["recaudado"].decimalValue(), "recaudado")
        igual("8.0000", mercado["participacionesEmitidas"].decimalValue(), "emitidas")
        igual("92.0000", mercado["participacionesDisponibles"].decimalValue(), "disponibles")
        // 150 * (1 + 8/100) = 162
        igual("162.0000", mercado["precioActual"].decimalValue(), "precio actual")
    }

    @Test
    fun `no se puede fundar un proyecto sin saldo suficiente`() {
        val colegio = crearColegio("IES Sin Fondos", "sinfondos@ies.example")
        val pobre = crearAlumno(colegio, "pobre@ies.example", saldo = "100.00")

        mockMvc.post("/api/v1/proyectos") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("pobre@ies.example")}")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Proyecto caro", "descripcion": "No me lo puedo permitir",
                  "idCategoria": 1, "idEstado": 2, "inversionInicial": 5000.00
                }
            """.trimIndent()
        }.andExpect { status { isConflict() } }

        // Nada se ha escrito: ni proyecto, ni descuento.
        igual("100.00", saldoDe(pobre), "saldo intacto")
        mockMvc.get("/api/v1/proyectos?idColegio=$colegio") { con(tokenDe("pobre@ies.example")) }.andExpect {
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `otro alumno invierte y el precio sube para el siguiente`() {
        val colegio = crearColegio("IES Inversores", "inversores@ies.example")
        val fundador = crearAlumno(colegio, "f.inv@ies.example", saldo = "10000.00")
        val inversor = crearAlumno(colegio, "i.inv@ies.example", saldo = "10000.00")

        val proyecto = crearProyecto(fundador, "Proyecto invertible", "1000.00", "100.00", "100.0000")
        // 1000/100 = 10 emitidas -> precio 100 * (1 + 10/100) = 110

        val recibo = invertir(inversor, proyecto, "5.0000")
        igual("110.0000", recibo["precioUnitario"].decimalValue(), "precio unitario")
        igual("550.00", recibo["importe"].decimalValue(), "importe")
        igual("9450.00", recibo["saldoCartera"].decimalValue(), "saldo tras invertir")
        // Tras la compra hay 15 emitidas -> 100 * (1 + 15/100) = 115
        igual("115.0000", recibo["precioSiguiente"].decimalValue(), "precio siguiente")

        // La segunda compra del mismo alumno paga mas cara y mueve el precio medio.
        val segundo = invertir(inversor, proyecto, "5.0000")
        igual("115.0000", segundo["precioUnitario"].decimalValue(), "precio de la segunda")
        igual("10.0000", segundo["participacionesTotales"].decimalValue(), "posicion")
        // (5*110 + 5*115) / 10 = 112.50
        igual("112.5000", segundo["precioMedio"].decimalValue(), "precio medio")

        val portafolio = json.readTree(
            mockMvc.get("/api/v1/portafolio") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.inv@ies.example")}")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )
        assertEquals(1, portafolio.size())
        igual("10.0000", portafolio[0]["participaciones"].decimalValue(), "participaciones")
        igual("112.5000", portafolio[0]["precioMedio"].decimalValue(), "precio medio")
    }

    @Test
    fun `el precio lo pone el servidor aunque el cliente mande otro`() {
        val colegio = crearColegio("IES Precio", "precio@ies.example")
        val fundador = crearAlumno(colegio, "f.precio@ies.example", saldo = "10000.00")
        val inversor = crearAlumno(colegio, "i.precio@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Precio fijado", "1000.00", "100.00", "100.0000")

        // Decir que se vio un precio ridiculo no compra barato: se rechaza.
        mockMvc.post("/api/v1/inversiones") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.precio@ies.example")}")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"idProyecto": $proyecto, "participaciones": 1.0000, "precioUnitarioEsperado": 0.01}
            """.trimIndent()
        }.andExpect { status { isConflict() } }

        igual("10000.00", saldoDe(inversor), "saldo intacto")
    }

    @Test
    fun `un alumno no puede invertir en su propio proyecto`() {
        val colegio = crearColegio("IES Propio", "propio@ies.example")
        val fundador = crearAlumno(colegio, "f.propio@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Mi propio proyecto", "1000.00", "100.00", "100.0000")

        mockMvc.post("/api/v1/inversiones") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("f.propio@ies.example")}")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"idProyecto": $proyecto, "participaciones": 1.0000}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `no se puede invertir en un proyecto de otro centro`() {
        val centroA = crearColegio("IES Centro Uno", "centro1@ies.example")
        val centroB = crearColegio("IES Centro Dos", "centro2@ies.example")
        val fundador = crearAlumno(centroA, "f.centro@ies.example", saldo = "10000.00")
        crearAlumno(centroB, "ajeno@ies.example", saldo = "10000.00")

        val proyecto = crearProyecto(fundador, "Proyecto del centro A", "1000.00", "100.00", "100.0000")

        mockMvc.post("/api/v1/inversiones") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("ajeno@ies.example")}")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"idProyecto": $proyecto, "participaciones": 1.0000}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `sin Idempotency-Key no se compra`() {
        val colegio = crearColegio("IES Sin Clave", "sinclave@ies.example")
        val fundador = crearAlumno(colegio, "f.clave@ies.example", saldo = "10000.00")
        crearAlumno(colegio, "i.clave@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Sin clave", "1000.00", "100.00", "100.0000")

        mockMvc.post("/api/v1/inversiones") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.clave@ies.example")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idProyecto": $proyecto, "participaciones": 1.0000}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `repetir la peticion con la misma clave no cobra dos veces`() {
        val colegio = crearColegio("IES Idempotente", "idem@ies.example")
        val fundador = crearAlumno(colegio, "f.idem@ies.example", saldo = "10000.00")
        val inversor = crearAlumno(colegio, "i.idem@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Idempotente", "1000.00", "100.00", "100.0000")

        val clave = UUID.randomUUID().toString()
        val primera = comprar(inversor = "i.idem@ies.example", proyecto, "3.0000", clave)
        val segunda = comprar(inversor = "i.idem@ies.example", proyecto, "3.0000", clave)

        // El reintento devuelve 200 con el recibo ORIGINAL, no un error ni una compra nueva.
        assertEquals(primera["idOperacion"].asInt(), segunda["idOperacion"].asInt())
        igual(primera["importe"].decimalValue().toPlainString(), segunda["importe"].decimalValue())
        igual("3.0000", segunda["participacionesTotales"].decimalValue(), "posicion")

        // Solo se cobro una vez.
        igual(
            BigDecimal("10000.00").subtract(primera["importe"].decimalValue()).toPlainString(),
            saldoDe(inversor),
            "saldo tras el reintento",
        )
    }

    /**
     * El reintento llega cuando la primera compra ya ha movido el precio. Si el servidor
     * validase el precio antes de reconocer la clave, contestaria 409 a una compra que SI
     * se hizo, y la app la daria por fallida y dejaria comprar otra vez.
     */
    @Test
    fun `el reintento con la misma clave devuelve el recibo aunque el precio ya haya subido`() {
        val colegio = crearColegio("IES Reintento", "reintento@ies.example")
        val fundador = crearAlumno(colegio, "f.rein@ies.example", saldo = "10000.00")
        val inversor = crearAlumno(colegio, "i.rein@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Reintento", "1000.00", "100.00", "100.0000")

        // 10 emitidas por el fundador: 100 * (1 + 10/100) = 110
        val clave = UUID.randomUUID().toString()
        val primera = comprar("i.rein@ies.example", proyecto, "5.0000", clave, precioEsperado = "110.0000")
        val segunda = comprar("i.rein@ies.example", proyecto, "5.0000", clave, precioEsperado = "110.0000")

        assertEquals(primera["idOperacion"].asInt(), segunda["idOperacion"].asInt())
        igual("5.0000", segunda["participacionesTotales"].decimalValue(), "posicion")
        igual("9450.00", saldoDe(inversor), "saldo: cobrado una sola vez")
    }

    @Test
    fun `el reintento de la compra que agoto la ronda tambien devuelve su recibo`() {
        val colegio = crearColegio("IES Ronda Llena", "llena@ies.example")
        val fundador = crearAlumno(colegio, "f.llena@ies.example", saldo = "10000.00")
        crearAlumno(colegio, "i.llena@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Ronda llena", "1000.00", "100.00", "20.0000")

        val clave = UUID.randomUUID().toString()
        val primera = comprar("i.llena@ies.example", proyecto, "10.0000", clave)
        val segunda = comprar("i.llena@ies.example", proyecto, "10.0000", clave)

        assertEquals(primera["idOperacion"].asInt(), segunda["idOperacion"].asInt())
    }

    /**
     * Las operaciones que genera el propio servidor llevan claves predecibles. Si una
     * compra pudiera reutilizarlas, el servidor creeria que ya se habia cobrado y emitiria
     * participaciones sin mover dinero.
     */
    @Test
    fun `una clave de otra operacion no da participaciones gratis`() {
        val colegio = crearColegio("IES Clave Ajena", "ajena@ies.example")
        val fundador = crearAlumno(colegio, "f.ajena@ies.example", saldo = "10000.00")
        val inversor = crearAlumno(colegio, "i.ajena@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Clave ajena", "1000.00", "100.00", "100.0000")
        val otroProyecto = crearProyecto(inversor, "Del inversor", "500.00", "100.00", "100.0000")

        for (clave in listOf("concesion-inicial-$inversor", "creacion-proyecto-$otroProyecto")) {
            mockMvc.post("/api/v1/inversiones") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.ajena@ies.example")}")
                header("Idempotency-Key", clave)
                contentType = MediaType.APPLICATION_JSON
                content = """{"idProyecto": $proyecto, "participaciones": 5.0000}"""
            }.andExpect { status { isConflict() } }
        }

        // Ni una participacion emitida, ni un JICP movido.
        igual("9500.00", saldoDe(inversor), "saldo tras fundar su proyecto")
        val mercado = json.readTree(
            mockMvc.get("/api/v1/proyectos/$proyecto/mercado") { con(tokenDe("i.ajena@ies.example")) }
                .andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )
        igual("10.0000", mercado["participacionesEmitidas"].decimalValue(), "emitidas")
    }

    @Test
    fun `50 compras simultaneas con saldo para una sola y solo una prospera`() {
        val colegio = crearColegio("IES Concurrencia", "concurrencia@ies.example")
        val fundador = crearAlumno(colegio, "f.conc@ies.example", saldo = "10000.00")

        // Saldo justo para UNA compra de 1 participacion a 110.
        val justo = crearAlumno(colegio, "justo@ies.example", saldo = "110.00")
        val proyecto = crearProyecto(fundador, "Contencion", "1000.00", "100.00", "100.0000")
        val token = tokenDe("justo@ies.example")

        val intentos = 50
        val barrera = CyclicBarrier(intentos)
        val pool = Executors.newFixedThreadPool(intentos)

        // Clave distinta por intento: sin idempotencia que los una, es la contabilidad la
        // que tiene que impedir que se gaste el mismo saldo dos veces.
        val tareas = (1..intentos).map {
            Callable {
                barrera.await(10, TimeUnit.SECONDS)
                mockMvc.post("/api/v1/inversiones") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"idProyecto": $proyecto, "participaciones": 1.0000}"""
                }.andReturn().response.status
            }
        }

        val codigos = pool.invokeAll(tareas).map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        val exitos = codigos.count { it == 200 }
        assertEquals(1, exitos, "solo una compra debe prosperar; codigos=$codigos")

        // El saldo nunca queda negativo.
        val saldoFinal = saldoDe(justo)
        assertTrue(saldoFinal >= BigDecimal.ZERO, "el saldo quedo en negativo: $saldoFinal")
        igual("0.00", saldoFinal, "saldo tras la contienda")

        comprobarQueElLibroCuadra()
    }

    @Test
    fun `comentar un proyecto y leer el hilo`() {
        val colegio = crearColegio("IES Comentarios", "comentarios@ies.example")
        val fundador = crearAlumno(colegio, "f.com@ies.example", saldo = "10000.00")
        crearAlumno(colegio, "i.com@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Comentable", "1000.00", "100.00", "100.0000")

        mockMvc.post("/api/v1/proyectos/$proyecto/comentarios") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.com@ies.example")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"texto": "  Me parece muy buena idea, ¿como pensais monetizarlo?  "}"""
        }.andExpect {
            status { isCreated() }
            // El texto se recorta antes de guardar.
            jsonPath("$.texto") { value("Me parece muy buena idea, ¿como pensais monetizarlo?") }
            jsonPath("$.nombre") { value("Alumno") }
        }

        // Leer el hilo tambien exige token, como todo lo que no es el login.
        mockMvc.get("/api/v1/proyectos/$proyecto/comentarios") { con(tokenDe("f.com@ies.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    fun `un comentario vacio se rechaza con 422`() {
        val colegio = crearColegio("IES Vacio", "vacio@ies.example")
        val fundador = crearAlumno(colegio, "f.vacio@ies.example", saldo = "10000.00")
        val proyecto = crearProyecto(fundador, "Sin comentarios", "1000.00", "100.00", "100.0000")

        mockMvc.post("/api/v1/proyectos/$proyecto/comentarios") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("f.vacio@ies.example")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"texto": "   "}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `los movimientos de la cartera cuentan la historia completa`() {
        val colegio = crearColegio("IES Movimientos", "movs@ies.example")
        val fundador = crearAlumno(colegio, "f.mov@ies.example", saldo = "10000.00")
        // Sin ajuste de saldo: asi el historial es exactamente concesion + inversion.
        val inversor = crearAlumno(colegio, "i.mov@ies.example")
        val proyecto = crearProyecto(fundador, "Con historial", "1000.00", "100.00", "100.0000")
        invertir(inversor, proyecto, "2.0000")

        val movimientos = json.readTree(
            mockMvc.get("/api/v1/cartera/movimientos") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("i.mov@ies.example")}")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )
        assertEquals(2, movimientos["totalElements"].asInt())

        // El mas reciente primero: la inversion, en negativo.
        val inversion = movimientos["content"][0]
        assertEquals("INVERSION", inversion["tipo"].asText())
        igual("-220.00", inversion["importe"].decimalValue(), "importe de la inversion")
        assertEquals("Con historial", inversion["nombreProyecto"].asText())

        val concesion = movimientos["content"][1]
        assertEquals("CONCESION_INICIAL", concesion["tipo"].asText())
        // La concesion es la fija del servidor, no algo que eligiera quien dio el alta.
        igual(SALDO_INICIAL, concesion["importe"].decimalValue(), "concesion inicial")
    }

    @Test
    fun `la reconciliacion cuadra tras un dia de mercado`() {
        val colegio = crearColegio("IES Reconciliacion", "recon@ies.example")
        val fundador = crearAlumno(colegio, "f.rec@ies.example", saldo = "10000.00")
        val a = crearAlumno(colegio, "a.rec@ies.example", saldo = "8000.00")
        val b = crearAlumno(colegio, "b.rec@ies.example", saldo = "6000.00")

        val proyecto = crearProyecto(fundador, "Muy demandado", "2000.00", "200.00", "100.0000")
        invertir(a, proyecto, "3.0000")
        invertir(b, proyecto, "4.0000")
        invertir(a, proyecto, "2.0000")

        comprobarQueElLibroCuadra()
    }

    // --- utilidades ---

    /**
     * Compara importes por VALOR y no con equals.
     *
     * `BigDecimal("110.00").equals(BigDecimal("110.0"))` es false porque equals mira
     * tambien la escala, y la escala con la que Jackson devuelve un numero depende de como
     * se serializo. Lo que importa aqui es la cantidad de dinero, asi que se usa compareTo.
     */
    private fun igual(esperado: String, real: BigDecimal, que: String = "importe") =
        assertEquals(
            0, BigDecimal(esperado).compareTo(real),
            "$que: esperado $esperado pero fue $real",
        )

    private fun mercadoDe(idProyecto: Int) = json.readTree(
        mockMvc.get("/api/v1/proyectos/$idProyecto/mercado") { con(tokenDeAdmin()) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    /**
     * Las tres consultas que el README exige que salgan siempre a cero. Son la prueba de
     * que no se ha creado ni destruido dinero por el camino.
     */
    private fun comprobarQueElLibroCuadra() {
        assertEquals(
            emptyList(), apuntes.operacionesDescuadradas(),
            "hay operaciones cuyos apuntes no suman cero",
        )
        assertEquals(
            emptyList(), apuntes.cuentasDescuadradas(),
            "hay cuentas cuyo saldo no coincide con sus apuntes",
        )
        assertEquals(
            0, apuntes.masaMonetaria().compareTo(BigDecimal.ZERO),
            "la masa monetaria global deberia ser exactamente 0",
        )
    }



    private fun crearProyecto(
        idAlumno: Int,
        nombre: String,
        inversionInicial: String,
        precioBase: String,
        participacionesTotales: String,
    ): Int {
        val email = emailDe(idAlumno)
        return json.readTree(
            mockMvc.post("/api/v1/proyectos") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe(email)}")
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "$nombre", "descripcion": "Proyecto de prueba del mercado",
                      "idCategoria": 1, "idEstado": 2,
                      "inversionInicial": $inversionInicial,
                      "precioBase": $precioBase,
                      "participacionesTotales": $participacionesTotales
                    }
                """.trimIndent()
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asInt()
    }

    private fun invertir(idAlumno: Int, idProyecto: Int, participaciones: String) =
        comprar(emailDe(idAlumno), idProyecto, participaciones, UUID.randomUUID().toString())

    private fun comprar(
        inversor: String,
        idProyecto: Int,
        participaciones: String,
        clave: String,
        precioEsperado: String? = null,
    ) = json.readTree(
        mockMvc.post("/api/v1/inversiones") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe(inversor)}")
            header("Idempotency-Key", clave)
            contentType = MediaType.APPLICATION_JSON
            content = if (precioEsperado == null) {
                """{"idProyecto": $idProyecto, "participaciones": $participaciones}"""
            } else {
                """{"idProyecto": $idProyecto, "participaciones": $participaciones,
                    "precioUnitarioEsperado": $precioEsperado}"""
            }
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    private fun saldoDe(idAlumno: Int): BigDecimal = json.readTree(
        mockMvc.get("/api/v1/cartera") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe(emailDe(idAlumno))}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("saldo").decimalValue()

    private fun emailDe(idAlumno: Int): String = json.readTree(
        mockMvc.get("/api/v1/alumnos/$idAlumno") { con(tokenDeAdmin()) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("email").asText()
}
