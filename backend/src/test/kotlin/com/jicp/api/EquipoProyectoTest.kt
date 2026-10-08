package com.jicp.api

import com.jicp.api.proyecto.RolesProyecto
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Socios y colaboradores de un proyecto (tabla `alumno_proyecto`).
 *
 * Las operaciones sobre el equipo se hacen con el token del creador del proyecto, que es
 * quien las hara en la app.
 */
class EquipoProyectoTest : PruebaDeIntegracion() {

    @Test
    fun `el creador queda registrado como miembro al crear el proyecto`() {
        val colegio = crearColegio("IES Equipo Alta")
        val creador = alumno(colegio, "equipo.alta.creador")
        val proyecto = crearProyecto(creador, "Proyecto con autor")

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros") { con(token(creador)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].idAlumno") { value(creador) }
            jsonPath("$[0].rol.id") { value(RolesProyecto.CREADOR) }
            jsonPath("$[0].rol.nombre") { value("Creador") }
        }

        mockMvc.get("/api/v1/alumnos/$creador/proyectos") { con(token(creador)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].rol.nombre") { value("Creador") }
            jsonPath("$[0].proyecto.nombre") { value("Proyecto con autor") }
        }
    }

    @Test
    fun `se pueden anadir socios y colaboradores`() {
        val colegio = crearColegio("IES Equipo Socios")
        val creador = alumno(colegio, "equipo.socios.creador")
        val socio = alumno(colegio, "equipo.socios.socio")
        val colaborador = alumno(colegio, "equipo.socios.colaborador")
        val proyecto = crearProyecto(creador, "Proyecto en equipo")

        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect {
            status { isCreated() }
            jsonPath("$.idAlumno") { value(socio) }
            jsonPath("$.rol.nombre") { value("Socio") }
        }
        anadirMiembro(proyecto, colaborador, RolesProyecto.COLABORADOR).andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros") { con(token(creador)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            // Ordenados por rol: creador, socio, colaborador.
            jsonPath("$[0].rol.id") { value(RolesProyecto.CREADOR) }
            jsonPath("$[1].rol.id") { value(RolesProyecto.SOCIO) }
            jsonPath("$[2].rol.id") { value(RolesProyecto.COLABORADOR) }
        }

        // El socio ve el proyecto en su lista, con su propio rol.
        mockMvc.get("/api/v1/alumnos/$socio/proyectos") { con(token(socio)) }.andExpect {
            status { isOk() }
            jsonPath("$[0].rol.nombre") { value("Socio") }
            jsonPath("$[0].proyecto.creador.idAlumno") { value(creador) }
        }
    }

    @Test
    fun `un alumno no puede participar dos veces en el mismo proyecto`() {
        val colegio = crearColegio("IES Equipo Duplicado")
        val creador = alumno(colegio, "equipo.dup.creador")
        val socio = alumno(colegio, "equipo.dup.socio")
        val proyecto = crearProyecto(creador, "Proyecto sin duplicados")

        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }
        anadirMiembro(proyecto, socio, RolesProyecto.COLABORADOR).andExpect { status { isConflict() } }

        // Tampoco el creador puede volver a apuntarse con otro rol.
        anadirMiembro(proyecto, creador, RolesProyecto.SOCIO).andExpect { status { isConflict() } }
    }

    @Test
    fun `no se puede conceder el rol de creador despues del alta`() {
        val colegio = crearColegio("IES Equipo Creador")
        val creador = alumno(colegio, "equipo.creador.uno")
        val otro = alumno(colegio, "equipo.creador.dos")
        val proyecto = crearProyecto(creador, "Proyecto con un solo autor")

        anadirMiembro(proyecto, otro, RolesProyecto.CREADOR).andExpect { status { isConflict() } }

        mockMvc.get("/api/v1/proyectos/$proyecto/miembros") { con(token(creador)) }.andExpect {
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `un alumno de otro colegio no puede unirse al proyecto`() {
        val colegio = crearColegio("IES Equipo Propio")
        val ajeno = crearColegio("IES Equipo Ajeno")
        val creador = alumno(colegio, "equipo.colegio.creador")
        val forastero = alumno(ajeno, "equipo.colegio.forastero")
        val proyecto = crearProyecto(creador, "Proyecto de un solo centro")

        anadirMiembro(proyecto, forastero, RolesProyecto.SOCIO).andExpect { status { isConflict() } }
    }

    @Test
    fun `se puede cambiar el rol de un socio pero no el del creador`() {
        val colegio = crearColegio("IES Equipo Roles")
        val creador = alumno(colegio, "equipo.roles.creador")
        val socio = alumno(colegio, "equipo.roles.socio")
        val proyecto = crearProyecto(creador, "Proyecto con cambios de rol")
        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }

        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$socio") {
            con(token(creador))
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.COLABORADOR}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rol.nombre") { value("Colaborador") }
        }

        // La autoria no se transfiere...
        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$socio") {
            con(token(creador))
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.CREADOR}}"""
        }.andExpect { status { isConflict() } }

        // ...ni el creador se degrada.
        mockMvc.put("/api/v1/proyectos/$proyecto/miembros/$creador") {
            con(token(creador))
            contentType = MediaType.APPLICATION_JSON
            content = """{"idRol": ${RolesProyecto.SOCIO}}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `un socio puede salir del proyecto pero el creador no`() {
        val colegio = crearColegio("IES Equipo Bajas")
        val creador = alumno(colegio, "equipo.bajas.creador")
        val socio = alumno(colegio, "equipo.bajas.socio")
        val proyecto = crearProyecto(creador, "Proyecto con bajas")
        anadirMiembro(proyecto, socio, RolesProyecto.SOCIO).andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$socio") { con(token(creador)) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/proyectos/$proyecto/miembros") { con(token(creador)) }.andExpect {
            jsonPath("$.length()") { value(1) }
        }

        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$creador") { con(token(creador)) }.andExpect { status { isConflict() } }

        // Quien ya no participa no aparece como miembro.
        mockMvc.delete("/api/v1/proyectos/$proyecto/miembros/$socio") { con(token(creador)) }.andExpect { status { isNotFound() } }
    }

    /** Lo hace el creador del proyecto, que es quien gestiona su equipo. */
    private fun anadirMiembro(idProyecto: Int, idAlumno: Int, idRol: Int) =
        mockMvc.post("/api/v1/proyectos/$idProyecto/miembros") {
            con(token(creadores.getValue(idProyecto)))
            contentType = MediaType.APPLICATION_JSON
            content = """{"idAlumno": $idAlumno, "idRol": $idRol}"""
        }

    private fun alumno(idColegio: Int, alias: String): Int {
        val id = crearAlumno(idColegio, "$alias@ies.example", apellido = alias)
        emails[id] = "$alias@ies.example"
        return id
    }

    /**
     * El alumno creador no viaja en el cuerpo: sale del token, asi que hay que entrar
     * como el. Los proyectos de estos tests se fundan sin inversion inicial, porque aqui
     * lo que se prueba es el equipo y no el dinero.
     */
    private fun crearProyecto(idAlumnoCreador: Int, nombre: String): Int {
        val respuesta = mockMvc.post("/api/v1/proyectos") {
            con(token(idAlumnoCreador))
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
        val id = json.readTree(respuesta).get("id").asInt()
        creadores[id] = idAlumnoCreador
        return id
    }

    private val emails = mutableMapOf<Int, String>()
    private val creadores = mutableMapOf<Int, Int>()

    private fun token(idAlumno: Int): String = tokenDe(emails.getValue(idAlumno))
}
