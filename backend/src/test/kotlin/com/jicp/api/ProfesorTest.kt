package com.jicp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.jicp.api.colegio.ColegioRepository
import com.jicp.api.profesor.Profesor
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresConfig::class)
class ProfesorTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var profesores: ProfesorRepository
    @Autowired private lateinit var colegios: ColegioRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `un profesor da de alta a un companero y lo hereda su centro`() {
        val idColegio = crearColegio("IES Docentes", "docentes@ies.example")
        val token = tokenDeProfesorSembrado(idColegio, "jefatura@docentes.example")

        val id = json.readTree(
            mockMvc.post("/api/v1/profesores") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "Marta",
                      "apellido": "Ruiz",
                      "email": "marta@docentes.example",
                      "password": "$CONTRASENA"
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("marta@docentes.example") }
                // El centro no se envia: sale del profesor que da el alta.
                jsonPath("$.idColegio") { value(idColegio) }
                jsonPath("$.nombreColegio") { value("IES Docentes") }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.passwordHash") { doesNotExist() }
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        mockMvc.get("/api/v1/profesores/$id").andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Marta") }
        }
    }

    @Test
    fun `sin token no se puede dar de alta profesorado`() {
        mockMvc.post("/api/v1/profesores") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Intruso", "apellido": "Sin Token",
                  "email": "intruso@docentes.example", "password": "$CONTRASENA"
                }
            """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("No autenticado") }
        }
    }

    @Test
    fun `un alumno no puede dar de alta profesorado`() {
        val idColegio = crearColegio("IES Alumno Cuela", "alumnocuela@ies.example")
        val token = tokenDeAlumno(idColegio, "alumno.cuela@ies.example")

        // Token valido, pero el rol no alcanza: 403, no 401.
        mockMvc.post("/api/v1/profesores") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Colado", "apellido": "Por Alumno",
                  "email": "colado@docentes.example", "password": "$CONTRASENA"
                }
            """.trimIndent()
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un profesor no puede editar a otro de un centro distinto`() {
        val idCentroA = crearColegio("IES Centro A", "centroa@ies.example")
        val idCentroB = crearColegio("IES Centro B", "centrob@ies.example")

        val tokenA = tokenDeProfesorSembrado(idCentroA, "jefe.a@docentes.example")
        val idProfesorB = profesorSembrado(idCentroB, "jefe.b@docentes.example").let {
            requireNotNull(it.id)
        }

        mockMvc.put("/api/v1/profesores/$idProfesorB") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "Secuestrado", "apellido": "Por Otro Centro"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.title") { value("Acceso denegado") }
        }
    }

    @Test
    fun `un profesor si puede editar a alguien de su propio centro`() {
        val idColegio = crearColegio("IES Mismo Centro", "mismocentro@ies.example")
        val token = tokenDeProfesorSembrado(idColegio, "jefe.mismo@docentes.example")
        val companero = profesorSembrado(idColegio, "companero@docentes.example")

        mockMvc.put("/api/v1/profesores/${companero.id}") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "Marta Elena", "apellido": "Ruiz Vega"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Marta Elena") }
            // Cambiar el nombre no toca el email, que vive en usuario.
            jsonPath("$.email") { value("companero@docentes.example") }
        }
    }

    @Test
    fun `el profesor entra con el mismo login que un alumno y su rol lo dice el servidor`() {
        val idColegio = crearColegio("IES Login Docente", "logindoc@ies.example")
        profesorSembrado(idColegio, "profe.login@docentes.example")

        val token = json.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "profe.login@docentes.example", "password": "$CONTRASENA"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.rol") { value("PROFESOR") }
            }.andReturn().response.contentAsString,
        ).get("accessToken").asText()

        mockMvc.get("/api/v1/yo") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.rol") { value("PROFESOR") }
            jsonPath("$.profesor.nombreColegio") { value("IES Login Docente") }
            // Un profesor no tiene datos de alumno: el campo viene vacio, no a medias.
            jsonPath("$.alumno") { doesNotExist() }
        }
    }

    @Test
    fun `un alumno y un profesor no pueden compartir email`() {
        val idColegio = crearColegio("IES Compartido", "compartido@ies.example")
        val token = tokenDeProfesorSembrado(idColegio, "jefe.compartido@docentes.example")

        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Ana", "apellido": "Lopez",
                  "email": "mismo@ies.example", "password": "$CONTRASENA",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }

        // El email es unico en usuario, que es de donde cuelgan los dos roles.
        mockMvc.post("/api/v1/profesores") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Ana", "apellido": "Lopez",
                  "email": "mismo@ies.example", "password": "$CONTRASENA"
                }
            """.trimIndent()
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `el listado admite filtrar por colegio`() {
        val idColegio = crearColegio("IES Filtro Docente", "filtrodoc@ies.example")
        profesorSembrado(idColegio, "filtro1@docentes.example")
        profesorSembrado(idColegio, "filtro2@docentes.example")

        mockMvc.get("/api/v1/profesores?idColegio=$idColegio").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
        }

        mockMvc.get("/api/v1/profesores?idColegio=999999").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `los datos invalidos devuelven 422 con el detalle por campo`() {
        val idColegio = crearColegio("IES Validacion", "validacion@ies.example")
        val token = tokenDeProfesorSembrado(idColegio, "jefe.validacion@docentes.example")

        mockMvc.post("/api/v1/profesores") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "", "apellido": "Corto", "email": "no-es-email", "password": "corta"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errores.nombre") { exists() }
            jsonPath("$.errores.email") { exists() }
            jsonPath("$.errores.password") { exists() }
        }
    }

    // --- utilidades ---

    private fun crearColegio(nombre: String, email: String): Int {
        val respuesta = mockMvc.post("/api/v1/colegios") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "$nombre", "direccion": "Calle Mayor 1", "email": "$email"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return json.readTree(respuesta).get("id").asInt()
    }

    /**
     * Siembra un profesor directamente en la base, que es lo unico que se puede hacer
     * cuando todavia no hay ninguno: por la API ya no se puede, y ese es justo el
     * comportamiento que se quiere. Equivale a lo que hace ProfesorInicial al arrancar.
     */
    private fun profesorSembrado(idColegio: Int, email: String): Profesor =
        profesores.save(
            Profesor(
                nombre = "Profesor",
                apellido = "Sembrado",
                usuario = Usuario(
                    email = email,
                    passwordHash = passwordEncoder.encode(CONTRASENA),
                    rol = Rol.PROFESOR,
                ),
                colegio = colegios.findById(idColegio).orElseThrow(),
            ),
        )

    private fun tokenDeProfesorSembrado(idColegio: Int, email: String): String {
        profesorSembrado(idColegio, email)
        return tokenDe(email)
    }

    private fun tokenDeAlumno(idColegio: Int, email: String): String {
        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Alumno", "apellido": "De Prueba",
                  "email": "$email", "password": "$CONTRASENA",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }
        return tokenDe(email)
    }

    private fun tokenDe(email: String): String {
        val respuesta = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "password": "$CONTRASENA"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return json.readTree(respuesta).get("accessToken").asText()
    }

    private companion object {
        const val CONTRASENA = "contrasena-larga"
    }
}
