package com.jicp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.jicp.api.proyecto.RolesProyecto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Socios y colaboradores de un proyecto (tabla `alumno_proyecto`). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresConfig::class)
class EquipoProyectoTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var json: ObjectMapper

    @Test
    fun `el creador queda registrado como miembro al crear el proyecto`() {
        val colegio = crearColegio("IES Equipo Alta")
        val creador = crearAlumno(colegio, "equipo.alta.creador")
        val proyecto = crearProyecto(creador, "Proyecto con autor")

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].idAlumno") { value(creador) }
            jsonPath("$[0].rol.id") { value(RolesProyecto.CREADOR) }
            jsonPath("$[0].rol.nombre") { value("Creador") }
        }

        mockMvc.get("/api/v1/alumnos/$creador/proyectos").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].rol.nombre") { value("Creador") }
            jsonPath("$[0].proyecto.nombre") { value("Proyecto con autor") }
        }
    }

    @Test
    fun `se pueden anadir socios y colaboradores`() {
        val colegio = crearColegio("IES Equipo Socios")
        val creador = crearAlumno(colegio, "equipo.socios.creador")
        val socio = crearAlumno(colegio, "equipo.socios.socio")
        val colaborador = crearAlumno(colegio, "equipo.socios.colaborador")
        val proyecto = crearProyecto(creador, "Proyecto en equipo")

        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect {
            status { isCreated() }
            jsonPath("$.idAlumno") { value(socio) }
            jsonPath("$.rol.nombre") { value("Socio") }
        }
        anadirMiembro(proyecto, colaborador, RolesProyecto.COLABORADOR).andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            // Ordenados por rol: creador, socio, colaborador.
            jsonPath("$[0].rol.id") { value(RolesProyecto.CREADOR) }
            jsonPath("$[1].rol.id") { value(RolesProyecto.SOCIO) }
            jsonPath("$[2].rol.id") { value(RolesProyecto.COLABORADOR) }
        }

        // El socio ve el proyecto en su lista, con su propio rol.
        mockMvc.get("/api/v1/alumnos/$socio/proyectos").andExpect {
            status { isOk() }
            jsonPath("$[0].rol.nombre") { value("Socio") }
            jsonPath("$[0].proyecto.creador.idAlumno") { value(creador) }
        }
    }

    @Test
    fun `un alumno no puede participar dos veces en el mismo proyecto`() {
        val colegio = crearColegio("IES Equipo Duplicado")
        val creador = crearAlumno(colegio, "equipo.dup.creador")
        val socio = crearAlumno(colegio, "equipo.dup.socio")
        val proyecto = crearProyecto(creador, "Proyecto sin duplicados")

        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }
        anadirMiembro(proyecto, socio, RolesProyecto.COLABORADOR).andExpect { status { isConflict() } }

        // Tampoco el creador puede volver a apuntarse con otro rol.
        anadirMiembro(proyecto, creador, RolesProyecto.SOCIO).andExpect { status { isConflict() } }
    }

    @Test
    fun `no se puede conceder el rol de creador despues del alta`() {
        val colegio = crearColegio("IES Equipo Creador")
        val creador = crearAlumno(colegio, "equipo.creador.uno")
        val otro = crearAlumno(colegio, "equipo.creador.dos")
        val proyecto = crearProyecto(creador, "Proyecto con un solo autor")

        anadirMiembro(proyecto, otro, RolesProyecto.CREADOR).andExpect { status { isConflict() } }

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros").andExpect {
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `un alumno de otro colegio no puede unirse al proyecto`() {
        val colegio = crearColegio("IES Equipo Propio")
        val ajeno = crearColegio("IES Equipo Ajeno")
        val creador = crearAlumno(colegio, "equipo.colegio.creador")
        val forastero = crearAlumno(ajeno, "equipo.colegio.forastero")
        val proyecto = crearProyecto(creador, "Proyecto de un solo centro")

        anadirMiembro(proyecto, forastero, RolesProyecto.SOCIO).andExpect { status { isConflict() } }
    }

    @Test
    fun `se puede cambiar el rol de un socio pero no el del creador`() {
        val colegio = crearColegio("IES Equipo Roles")
        val creador = crearAlumno(colegio, "equipo.roles.creador")
        val socio = crearAlumno(colegio, "equipo.roles.socio")
        val proyecto = crearProyecto(creador, "Proyecto con cambios de rol")
        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }

        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$socio") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.COLABORADOR}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rol.nombre") { value("Colaborador") }
        }

        // La autoria no se transfiere...
        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$socio") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.CREADOR}}"""
        }.andExpect { status { isConflict() } }

        // ...ni el creador se degrada.
        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$creador") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.SOCIO}}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `un socio puede salir del proyecto pero el creador no`() {
        val colegio = crearColegio("IES Equipo Bajas")
        val creador = crearAlumno(colegio, "equipo.bajas.creador")
        val socio = crearAlumno(colegio, "equipo.bajas.socio")
        val proyecto = crearProyecto(creador, "Proyecto con bajas")
        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$socio").andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/proyectos/$proyecto/miembros").andExpect {
            jsonPath("$.length()") { value(1) }
        }

        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$creador").andExpect { status { isConflict() } }

        // Quien ya no participa no aparece como miembro.
        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$socio").andExpect { status { isNotFound() } }
    }

    private fun anadirMiembro(idProyecto: Int, idAlumno: Int, idRol: Int) =
        mockMvc.post("/api/v1/proyectos/$idProyecto/miembros") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"idAlumno": $idAlumno, "idRol": $idRol}"""
        }

    private fun crearColegio(nombre: String): Int {
        val respuesta = mockMvc.post("/api/v1/colegios") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"nombre": "$nombre", "direccion": "Calle Mayor 1", "email": "info@ies.example"}
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return json.readTree(respuesta).get("id").asInt()
    }

    private fun crearAlumno(idColegio: Int, alias: String): Int {
        val respuesta = mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Alumno",
                  "apellido": "$alias",
                  "email": "$alias@ies.example",
                  "password": "contrasena-larga",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = json.readTree(respuesta).get("id").asInt()
        emails[id] = "$alias@ies.example"
        return id
    }

    /**
     * El alumno creador ya no viaja en el cuerpo: sale del token, asi que hay que entrar
     * como el. Los proyectos de estos tests se fundan sin inversion inicial, porque aqui
     * lo que se prueba es el equipo y no el dinero.
     */
    private fun crearProyecto(idAlumnoCreador: Int, nombre: String): Int {
        val respuesta = mockMvc.post("/api/v1/proyectos") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe(idAlumnoCreador)}")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "$nombre",
                  "descripcion": "Proyecto de prueba del equipo",
                  "idCategoria": 1,
                  "idEstado": 2,
                  "inversionInicial": 0.00
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return json.readTree(respuesta).get("id").asInt()
    }

    private val emails = mutableMapOf<Int, String>()
    private val tokens = mutableMapOf<String, String>()

    private fun tokenDe(idAlumno: Int): String {
        val email = emails.getValue(idAlumno)
        return tokens.getOrPut(email) {
            val respuesta = mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "$email", "password": "contrasena-larga"}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            json.readTree(respuesta).get("accessToken").asText()
        }
    }
}
