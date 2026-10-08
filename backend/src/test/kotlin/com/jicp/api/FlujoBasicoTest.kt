package com.jicp.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class FlujoBasicoTest : PruebaDeIntegracion() {

    @Test
    fun `los catalogos se cargan con las migraciones`() {
        val token = tokenDeAdmin()
        mockMvc.get("/api/v1/categorias") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(5) }
        }
        mockMvc.get("/api/v1/estados-proyecto") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
        }
        mockMvc.get("/api/v1/roles-proyecto") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[0].nombre") { value("Creador") }
        }
    }

    @Test
    fun `alta de colegio, alumno y proyecto`() {
        val idColegio = crearColegio("IES Ramon y Cajal", "alta@ies.example")

        // El alumno se crea y la respuesta NO expone la contrasena ni su hash. Y aunque la
        // peticion intente fijar su saldo inicial, entra con el que decide el servidor.
        val idAlumno = json.readTree(
            mockMvc.post("/api/v1/alumnos") {
                con(tokenDeAdmin())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "Ana",
                      "apellido": "Martinez",
                      "email": "ana@ies.example",
                      "password": "$CONTRASENA",
                      "idColegio": $idColegio,
                      "jicpInicial": 99999999.00
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("ana@ies.example") }
                jsonPath("$.jicpInicial") { value(SALDO_INICIAL.toDouble()) }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.passwordHash") { doesNotExist() }
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        mockMvc.get("/api/v1/cartera") { con(tokenDe("ana@ies.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.saldo") { value(SALDO_INICIAL.toDouble()) }
        }

        val idProyecto = json.readTree(
            mockMvc.post("/api/v1/proyectos") {
                con(tokenDe("ana@ies.example"))
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "Eco-Drone Delivery",
                      "descripcion": "Drones autonomos para entrega sostenible",
                      "idCategoria": 1,
                      "idEstado": 2,
                      "inversionInicial": 1200.00
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.categoria.nombre") { value("Tecnología") }
                jsonPath("$.estado.nombre") { value("Publicado") }
                // El colegio no se envia: se hereda del alumno creador.
                jsonPath("$.idColegio") { value(idColegio) }
                jsonPath("$.nombreColegio") { value("IES Ramon y Cajal") }
                jsonPath("$.creador.idAlumno") { value(idAlumno) }
                jsonPath("$.creador.nombre") { value("Ana") }
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        mockMvc.get("/api/v1/proyectos/$idProyecto") { con(tokenDe("ana@ies.example")) }.andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Eco-Drone Delivery") }
            jsonPath("$.creador.apellido") { value("Martinez") }
        }
    }

    @Test
    fun `el listado de proyectos admite filtros opcionales`() {
        val idColegio = crearColegio("IES Filtros", "filtros@ies.example")
        val idAlumno = crearAlumno(idColegio, "filtros.alumno@ies.example")
        val token = tokenDe("filtros.alumno@ies.example")

        mockMvc.post("/api/v1/proyectos") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Proyecto filtrable",
                  "descripcion": "Sirve para comprobar los parametros opcionales",
                  "idCategoria": 1,
                  "idEstado": 2
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }

        // Sin filtros: la consulta con los tres parametros a null debe resolverse.
        mockMvc.get("/api/v1/proyectos") { con(token) }.andExpect { status { isOk() } }

        // Filtrando por colegio solo aparece el proyecto de ese colegio, con su creador.
        mockMvc.get("/api/v1/proyectos?idColegio=$idColegio") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].nombre") { value("Proyecto filtrable") }
            jsonPath("$.content[0].creador.idAlumno") { value(idAlumno) }
        }

        // Filtro combinado que no case con nada devuelve una pagina vacia.
        mockMvc.get("/api/v1/proyectos?idColegio=$idColegio&idCategoria=999999") { con(token) }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    /**
     * Ya no existe el caso "proyecto de un alumno inexistente": el creador sale del token,
     * asi que no se puede nombrar a otro. Lo que si se comprueba es que sin token no se
     * pueda fundar nada, que es la version fuerte de aquella prueba.
     */
    @Test
    fun `sin token no se puede crear un proyecto`() {
        mockMvc.post("/api/v1/proyectos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Proyecto huerfano",
                  "descripcion": "No deberia llegar a existir",
                  "idCategoria": 1,
                  "idEstado": 1
                }
            """.trimIndent()
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `no se admiten dos alumnos con el mismo email`() {
        val idColegio = crearColegio("IES Duplicados", "dup@ies.example")
        val cuerpo = """
            {
              "nombre": "Juan",
              "apellido": "Perez",
              "email": "repetido@ies.example",
              "password": "$CONTRASENA",
              "idColegio": $idColegio
            }
        """.trimIndent()

        mockMvc.post("/api/v1/alumnos") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/alumnos") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `un alumno de un colegio inexistente devuelve 404`() {
        mockMvc.post("/api/v1/alumnos") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Sin",
                  "apellido": "Colegio",
                  "email": "sincolegio@ies.example",
                  "password": "$CONTRASENA",
                  "idColegio": 999999
                }
            """.trimIndent()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `los datos invalidos devuelven 422 con el detalle por campo`() {
        mockMvc.post("/api/v1/alumnos") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "",
                  "apellido": "Corta",
                  "email": "esto-no-es-un-email",
                  "password": "corta",
                  "idColegio": 1
                }
            """.trimIndent()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errores.nombre") { exists() }
            jsonPath("$.errores.email") { exists() }
            jsonPath("$.errores.password") { exists() }
        }
    }
}
