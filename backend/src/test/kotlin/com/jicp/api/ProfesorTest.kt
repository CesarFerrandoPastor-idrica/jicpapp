package com.jicp.api

import com.jicp.api.colegio.ColegioRepository
import com.jicp.api.profesor.Profesor
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class ProfesorTest : PruebaDeIntegracion() {

    @Autowired private lateinit var profesores: ProfesorRepository
    @Autowired private lateinit var colegios: ColegioRepository

    @Test
    fun `un admin da de alta un profesor en el centro indicado`() {
        val idColegio = crearColegio("IES Docentes", "docentes@ies.example")

        val id = json.readTree(
            mockMvc.post("/api/v1/profesores") {
                con(tokenDeAdmin())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "Marta",
                      "apellido": "Ruiz",
                      "email": "marta@docentes.example",
                      "password": "$CONTRASENA",
                      "idColegio": $idColegio
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("marta@docentes.example") }
                jsonPath("$.idColegio") { value(idColegio) }
                jsonPath("$.nombreColegio") { value("IES Docentes") }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.passwordHash") { doesNotExist() }
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        mockMvc.get("/api/v1/profesores/$id") { con(tokenDeAdmin()) }.andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Marta") }
        }
    }

    @Test
    fun `sin token no se puede dar de alta profesorado`() {
        mockMvc.post("/api/v1/profesores") {
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlta("intruso@docentes.example", idColegio = 1)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("No autenticado") }
        }
    }

    @Test
    fun `un alumno no puede dar de alta profesorado`() {
        val idColegio = crearColegio("IES Alumno Cuela", "alumnocuela@ies.example")
        crearAlumno(idColegio, "alumno.cuela@ies.example")

        // Token valido, pero el rol no alcanza: 403, no 401.
        mockMvc.post("/api/v1/profesores") {
            con(tokenDe("alumno.cuela@ies.example"))
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlta("colado@docentes.example", idColegio)
        }.andExpect { status { isForbidden() } }
    }

    /** Las altas de usuarios son de los desarrolladores (ADMIN), tambien las de profesorado. */
    @Test
    fun `un profesor ya no puede dar de alta a un companero`() {
        val idColegio = crearColegio("IES Sin Altas", "sinaltas@ies.example")
        profesorSembrado(idColegio, "jefe.sinaltas@docentes.example")

        mockMvc.post("/api/v1/profesores") {
            con(tokenDe("jefe.sinaltas@docentes.example"))
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlta("companero.sinaltas@docentes.example", idColegio)
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un profesor no puede editar a otro de un centro distinto`() {
        val idCentroA = crearColegio("IES Centro A", "centroa@ies.example")
        val idCentroB = crearColegio("IES Centro B", "centrob@ies.example")

        profesorSembrado(idCentroA, "jefe.a@docentes.example")
        val idProfesorB = requireNotNull(profesorSembrado(idCentroB, "jefe.b@docentes.example").id)

        mockMvc.put("/api/v1/profesores/$idProfesorB") {
            con(tokenDe("jefe.a@docentes.example"))
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
        profesorSembrado(idColegio, "jefe.mismo@docentes.example")
        val companero = profesorSembrado(idColegio, "companero@docentes.example")

        mockMvc.put("/api/v1/profesores/${companero.id}") {
            con(tokenDe("jefe.mismo@docentes.example"))
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
    fun `un admin puede editar profesorado de cualquier centro`() {
        val idColegio = crearColegio("IES Edicion Admin", "edicionadmin@ies.example")
        val profesor = profesorSembrado(idColegio, "editable@docentes.example")

        mockMvc.put("/api/v1/profesores/${profesor.id}") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "Corregido", "apellido": "Por Admin"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Corregido") }
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

        mockMvc.get("/api/v1/yo") { con(token) }.andExpect {
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
        crearAlumno(idColegio, "mismo@ies.example", nombre = "Ana", apellido = "Lopez")

        // El email es unico en usuario, que es de donde cuelgan los dos roles.
        mockMvc.post("/api/v1/profesores") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlta("mismo@ies.example", idColegio)
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `el listado admite filtrar por colegio`() {
        val idColegio = crearColegio("IES Filtro Docente", "filtrodoc@ies.example")
        profesorSembrado(idColegio, "filtro1@docentes.example")
        profesorSembrado(idColegio, "filtro2@docentes.example")
        val token = tokenDeAdmin()

        mockMvc.get("/api/v1/profesores?idColegio=$idColegio") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
        }

        mockMvc.get("/api/v1/profesores?idColegio=999999") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `los datos invalidos devuelven 422 con el detalle por campo`() {
        mockMvc.post("/api/v1/profesores") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"nombre": "", "apellido": "Corto", "email": "no-es-email", "password": "corta", "idColegio": 1}
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errores.nombre") { exists() }
            jsonPath("$.errores.email") { exists() }
            jsonPath("$.errores.password") { exists() }
        }
    }

    // --- utilidades ---

    private fun cuerpoDeAlta(email: String, idColegio: Int) = """
        {
          "nombre": "Profesor", "apellido": "Nuevo",
          "email": "$email", "password": "$CONTRASENA",
          "idColegio": $idColegio
        }
    """.trimIndent()

    /**
     * Siembra un profesor directamente en la base. Sirve para tener profesorado sin pasar
     * por el alta de admin cuando lo que se prueba es otra cosa (editar, entrar...).
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
}
