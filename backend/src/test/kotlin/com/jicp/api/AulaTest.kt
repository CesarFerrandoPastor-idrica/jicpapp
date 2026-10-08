package com.jicp.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Lo que ve y hace el profesorado con su aula: el mercado de su centro, el ranking de
 * sus alumnos y los cursos que crea y asigna.
 */
class AulaTest : PruebaDeIntegracion() {

    // --- mercado acotado al centro ---

    @Test
    fun `cada uno ve solo los proyectos de su centro aunque pida otro`() {
        val centroA = crearColegio("IES Aula A")
        val centroB = crearColegio("IES Aula B")
        crearAlumno(centroA, "fundador.a@aula.example")
        crearAlumno(centroB, "fundador.b@aula.example")
        crearProfesor(centroA, "profe.a@aula.example")
        fundar("fundador.a@aula.example", "Proyecto de A")
        fundar("fundador.b@aula.example", "Proyecto de B")

        // El profesor de A pide los de B cambiando el parametro: recibe los de A.
        mockMvc.get("/api/v1/proyectos?idColegio=$centroB") { con(tokenDe("profe.a@aula.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].nombre") { value("Proyecto de A") }
        }

        // Igual un alumno.
        mockMvc.get("/api/v1/proyectos?idColegio=$centroA") { con(tokenDe("fundador.b@aula.example")) }.andExpect {
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].nombre") { value("Proyecto de B") }
        }

        // El admin no pertenece a ningun centro: el filtro si le vale.
        mockMvc.get("/api/v1/proyectos?idColegio=$centroB") { con(tokenDeAdmin()) }.andExpect {
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].nombre") { value("Proyecto de B") }
        }
    }

    // --- ranking ---

    @Test
    fun `el ranking muestra solo al alumnado del centro ordenado por rentabilidad`() {
        val centro = crearColegio("IES Ranking")
        val otro = crearColegio("IES Ranking Ajeno")
        crearProfesor(centro, "profe.ranking@aula.example")
        val quieto = crearAlumno(centro, "quieto@aula.example", apellido = "Quieto")
        val fundador = crearAlumno(centro, "fundador.rk@aula.example", apellido = "Fundador")
        crearAlumno(otro, "forastero@aula.example", apellido = "Forastero")

        // Funda con 1000 a precio base 100 en una ronda de 100: se queda 10 participaciones
        // que ya valen 110 cada una. Patrimonio 499000 + 1100 = 500100.
        fundar("fundador.rk@aula.example", "Ranking", inversionInicial = "1000.00")

        mockMvc.get("/api/v1/ranking") { con(tokenDe("profe.ranking@aula.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }

            jsonPath("$[0].posicion") { value(1) }
            jsonPath("$[0].idAlumno") { value(fundador) }
            jsonPath("$[0].saldo") { value(499000.00) }
            jsonPath("$[0].valorParticipaciones") { value(1100.00) }
            jsonPath("$[0].patrimonio") { value(500100.00) }
            // (500100 - 500000) / 500000 = 0.02 %
            jsonPath("$[0].rentabilidad") { value(0.02) }

            jsonPath("$[1].idAlumno") { value(quieto) }
            jsonPath("$[1].rentabilidad") { value(0.00) }
        }
    }

    @Test
    fun `un alumno no puede ver el ranking`() {
        val centro = crearColegio("IES Ranking Cerrado")
        crearAlumno(centro, "curioso@aula.example")

        mockMvc.get("/api/v1/ranking") { con(tokenDe("curioso@aula.example")) }
            .andExpect { status { isForbidden() } }
    }

    // --- cursos ---

    @Test
    fun `un profesor crea un curso y solo lo ven sus alumnos asignados`() {
        val centro = crearColegio("IES Cursos")
        crearProfesor(centro, "profe.cursos@aula.example")
        val asignada = crearAlumno(centro, "asignada@aula.example")
        crearAlumno(centro, "sin.asignar@aula.example")

        mockMvc.post("/api/v1/cursos") {
            con(tokenDe("profe.cursos@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = curso("Finanzas personales", listOf(asignada, asignada), url = "https://ejemplo.org/video")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.titulo") { value("Finanzas personales") }
            jsonPath("$.urlRecurso") { value("https://ejemplo.org/video") }
            // El id repetido cuenta una vez.
            jsonPath("$.alumnosAsignados") { value(1) }
        }

        mockMvc.get("/api/v1/cursos") { con(tokenDe("profe.cursos@aula.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].alumnosAsignados") { value(1) }
            jsonPath("$[0].progreso") { doesNotExist() }
        }

        mockMvc.get("/api/v1/cursos") { con(tokenDe("asignada@aula.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].titulo") { value("Finanzas personales") }
            jsonPath("$[0].profesor") { value("Profesor De Prueba") }
            jsonPath("$[0].progreso") { value(0.0) }
        }

        mockMvc.get("/api/v1/cursos") { con(tokenDe("sin.asignar@aula.example")) }.andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `no se asigna un curso a alumnos de otro centro y no queda nada a medias`() {
        val centro = crearColegio("IES Cursos Propio")
        val otro = crearColegio("IES Cursos Ajeno")
        crearProfesor(centro, "profe.mezcla@aula.example")
        val propio = crearAlumno(centro, "propio@aula.example")
        val ajeno = crearAlumno(otro, "ajeno@aula.example")

        mockMvc.post("/api/v1/cursos") {
            con(tokenDe("profe.mezcla@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = curso("Mezclado", listOf(propio, ajeno))
        }.andExpect { status { isForbidden() } }

        mockMvc.get("/api/v1/cursos") { con(tokenDe("profe.mezcla@aula.example")) }.andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `un alumno no crea cursos`() {
        val centro = crearColegio("IES Cursos Alumno")
        val alumno = crearAlumno(centro, "alumno.cursos@aula.example")

        mockMvc.post("/api/v1/cursos") {
            con(tokenDe("alumno.cursos@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = curso("Por mi cuenta", listOf(alumno))
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `los cursos mal formados se rechazan`() {
        val centro = crearColegio("IES Cursos Validacion")
        crearProfesor(centro, "profe.validacion@aula.example")
        val alumno = crearAlumno(centro, "alumno.validacion@aula.example")
        val token = tokenDe("profe.validacion@aula.example")

        // Sin alumnos, sin titulo y con un enlace que no es http(s).
        mockMvc.post("/api/v1/cursos") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"titulo": "", "descripcion": "x", "urlRecurso": "javascript:alert(1)", "idsAlumnos": []}
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errores.titulo") { exists() }
            jsonPath("$.errores.urlRecurso") { exists() }
            jsonPath("$.errores.idsAlumnos") { exists() }
        }

        // Un alumno que no existe.
        mockMvc.post("/api/v1/cursos") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = curso("Fantasma", listOf(alumno, 999999))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `el profesor edita el contenido de su curso y nadie mas puede`() {
        val centro = crearColegio("IES Editar Curso")
        crearProfesor(centro, "autora@aula.example")
        crearProfesor(centro, "companero@aula.example")
        val alumno = crearAlumno(centro, "alumno.editar@aula.example")

        val idCurso = json.readTree(
            mockMvc.post("/api/v1/cursos") {
                con(tokenDe("autora@aula.example"))
                contentType = MediaType.APPLICATION_JSON
                content = curso("Borrador", listOf(alumno), url = "https://ejemplo.org/viejo")
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        // Un companero del mismo centro no puede tocarlo.
        mockMvc.put("/api/v1/cursos/$idCurso") {
            con(tokenDe("companero@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"titulo": "Secuestrado", "descripcion": "x"}"""
        }.andExpect { status { isForbidden() } }

        // La autora si: cambia el texto y quita el enlace. Los alumnos siguen asignados.
        mockMvc.put("/api/v1/cursos/$idCurso") {
            con(tokenDe("autora@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"titulo": "Finanzas II", "descripcion": "Ampliado con un tema nuevo"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.titulo") { value("Finanzas II") }
            jsonPath("$.urlRecurso") { doesNotExist() }
            jsonPath("$.alumnosAsignados") { value(1) }
        }

        // El alumno ve la version nueva.
        mockMvc.get("/api/v1/cursos") { con(tokenDe("alumno.editar@aula.example")) }.andExpect {
            jsonPath("$[0].titulo") { value("Finanzas II") }
            jsonPath("$[0].descripcion") { value("Ampliado con un tema nuevo") }
        }

        mockMvc.put("/api/v1/cursos/999999") {
            con(tokenDe("autora@aula.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"titulo": "No existe", "descripcion": "x"}"""
        }.andExpect { status { isNotFound() } }
    }

    // --- comentarios del profesorado ---

    @Test
    fun `el profesor comenta los proyectos de su centro y queda marcado como profesor`() {
        val centro = crearColegio("IES Comenta Profe")
        val otro = crearColegio("IES Comenta Ajeno")
        crearProfesor(centro, "profe.comenta@aula.example")
        crearAlumno(centro, "fundador.comenta@aula.example")
        crearAlumno(otro, "fundador.ajeno@aula.example")
        val propio = fundarYDevolverId("fundador.comenta@aula.example", "Propio")
        val ajeno = fundarYDevolverId("fundador.ajeno@aula.example", "Ajeno")
        val token = tokenDe("profe.comenta@aula.example")

        mockMvc.post("/api/v1/proyectos/$propio/comentarios") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"texto": "Buen planteamiento, revisad los costes"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.deProfesor") { value(true) }
            jsonPath("$.idAlumno") { doesNotExist() }
            jsonPath("$.nombre") { value("Profesor") }
        }

        // El hilo lo enseña a todos, con su autor.
        mockMvc.get("/api/v1/proyectos/$propio/comentarios") { con(tokenDe("fundador.comenta@aula.example")) }
            .andExpect {
                jsonPath("$.content[0].deProfesor") { value(true) }
                jsonPath("$.content[0].idProfesor") { exists() }
            }

        mockMvc.post("/api/v1/proyectos/$ajeno/comentarios") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"texto": "Me cuelo en otro centro"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `el admin no comenta`() {
        val centro = crearColegio("IES Comenta Admin")
        crearAlumno(centro, "fundador.admin@aula.example")
        val proyecto = fundarYDevolverId("fundador.admin@aula.example", "Sin admin")

        mockMvc.post("/api/v1/proyectos/$proyecto/comentarios") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """{"texto": "Hola"}"""
        }.andExpect { status { isForbidden() } }
    }

    // --- utilidades ---

    private fun fundarYDevolverId(email: String, nombre: String): Int = json.readTree(
        mockMvc.post("/api/v1/proyectos") {
            con(tokenDe(email))
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"nombre": "$nombre", "descripcion": "Proyecto del aula", "idCategoria": 1, "idEstado": 2}
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
    ).get("id").asInt()

    private fun fundar(email: String, nombre: String, inversionInicial: String = "0.00") {
        mockMvc.post("/api/v1/proyectos") {
            con(tokenDe(email))
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "$nombre", "descripcion": "Proyecto del aula",
                  "idCategoria": 1, "idEstado": 2,
                  "inversionInicial": $inversionInicial,
                  "precioBase": 100.00, "participacionesTotales": 100.0000
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }
    }

    private fun curso(titulo: String, idsAlumnos: List<Int>, url: String? = null) = """
        {
          "titulo": "$titulo",
          "descripcion": "Curso de prueba",
          ${if (url == null) "" else "\"urlRecurso\": \"$url\","}
          "idsAlumnos": ${idsAlumnos.joinToString(prefix = "[", postfix = "]")}
        }
    """.trimIndent()
}
