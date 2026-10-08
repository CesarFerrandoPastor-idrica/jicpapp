package com.jicp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.jicp.api.alumno.AlumnoRepository
import com.jicp.api.contabilidad.ContabilidadService
import com.jicp.api.contabilidad.CuentaRepository
import com.jicp.api.contabilidad.OrdenTransferencia
import com.jicp.api.contabilidad.TipoOperacion
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import com.jicp.api.seguridad.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID

/**
 * Base de los tests de integracion: contexto de Spring, PostgreSQL real y las ayudas
 * para preparar datos.
 *
 * Desde que la API nace cerrada, preparar un escenario ya no es llamar a `POST /colegios`
 * a pelo: las altas son de ADMIN. Esta clase lleva un admin de pruebas y hace las altas
 * en su nombre, igual que las haria un desarrollador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresConfig::class)
abstract class PruebaDeIntegracion {

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var json: ObjectMapper
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var usuarios: UsuarioRepository
    @Autowired private lateinit var alumnos: AlumnoRepository
    @Autowired private lateinit var cuentas: CuentaRepository
    @Autowired private lateinit var contabilidad: ContabilidadService

    /** Los logins se cachean: repetirlos en cada ayuda haria los tests mucho mas lentos. */
    private val tokens = mutableMapOf<String, String>()

    /** Pone el `Authorization: Bearer` en una peticion de MockMvc. */
    protected fun MockHttpServletRequestDsl.con(token: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    protected fun tokenDe(email: String): String = tokens.getOrPut(email) {
        val respuesta = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "password": "$CONTRASENA"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        json.readTree(respuesta).get("accessToken").asText()
    }

    /**
     * Token del admin de pruebas. Se siembra en la base si aun no existe, que es lo mismo
     * que hace AdminInicial al arrancar: el primer admin no puede nacer por la API.
     */
    protected fun tokenDeAdmin(): String {
        if (usuarios.findByEmail(EMAIL_ADMIN) == null) {
            usuarios.save(
                Usuario(
                    email = EMAIL_ADMIN,
                    passwordHash = passwordEncoder.encode(CONTRASENA),
                    rol = Rol.ADMIN,
                ),
            )
        }
        return tokenDe(EMAIL_ADMIN)
    }

    protected fun crearColegio(nombre: String, email: String = "info@ies.example"): Int {
        val respuesta = mockMvc.post("/api/v1/colegios") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "$nombre", "direccion": "Calle Mayor 1", "email": "$email"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return json.readTree(respuesta).get("id").asInt()
    }

    /**
     * Da de alta un alumno como lo haria un admin. Entra con el saldo inicial del servidor
     * ([SALDO_INICIAL]); si el test necesita otro, se pasa en [saldo] y se ajusta con un
     * apunte contable real (ver [ajustarSaldo]).
     */
    protected fun crearAlumno(
        idColegio: Int,
        email: String,
        saldo: String? = null,
        nombre: String = "Alumno",
        apellido: String = "De Prueba",
    ): Int {
        val respuesta = mockMvc.post("/api/v1/alumnos") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "$nombre", "apellido": "$apellido",
                  "email": "$email", "password": "$CONTRASENA",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = json.readTree(respuesta).get("id").asInt()

        if (saldo != null) ajustarSaldo(id, BigDecimal(saldo))
        return id
    }

    /** Da de alta un profesor en un centro, como lo haria un admin. */
    protected fun crearProfesor(idColegio: Int, email: String): Int {
        val respuesta = mockMvc.post("/api/v1/profesores") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Profesor", "apellido": "De Prueba",
                  "email": "$email", "password": "$CONTRASENA",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return json.readTree(respuesta).get("id").asInt()
    }

    /**
     * Deja la cartera de un alumno con el saldo indicado.
     *
     * Como el saldo inicial es fijo, los tests que necesitan uno concreto (por ejemplo,
     * "saldo justo para una compra") lo consiguen con una operacion `AJUSTE_ADMIN` contra
     * la cuenta de emision. Es dinero que pasa por el libro como cualquier otro, asi que
     * las consultas de reconciliacion siguen cuadrando a cero.
     */
    protected fun ajustarSaldo(idAlumno: Int, saldo: BigDecimal) {
        val alumno = alumnos.findById(idAlumno).orElseThrow()
        val cartera = requireNotNull(cuentas.carteraDe(idAlumno)) { "el alumno $idAlumno no tiene cartera" }
        val emision = requireNotNull(cuentas.emision()) { "no existe la cuenta de emision" }

        val diferencia = saldo.subtract(cartera.saldo)
        if (diferencia.signum() == 0) return

        val (origen, destino) = if (diferencia.signum() > 0) emision to cartera else cartera to emision
        contabilidad.ejecutar(
            OrdenTransferencia(
                tipo = TipoOperacion.AJUSTE_ADMIN,
                actor = alumno,
                idCuentaOrigen = origen.id!!,
                idCuentaDestino = destino.id!!,
                importe = diferencia.abs(),
                claveIdempotencia = "ajuste-test-${UUID.randomUUID()}",
            ),
        )
    }

    protected companion object {
        const val CONTRASENA = "contrasena-larga"

        /** El de `jicp.saldo-inicial` en application.yml. */
        const val SALDO_INICIAL = "500000.00"

        const val EMAIL_ADMIN = "admin@pruebas.example"
    }
}
