package com.jicp.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresConfig::class)
class FlujoBasicoTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var json: ObjectMapper

    @Test
    fun `los catalogos se cargan con las migraciones`() {
        mockMvc.get("/api/v1/categorias").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(5) }
        }
        mockMvc.get("/api/v1/estados-proyecto").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
        }
        mockMvc.get("/api/v1/roles-proyecto").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[0].nombre") { value("Creador") }
        }
    }

    @Test
    fun `alta de colegio, alumno y proyecto`() {
        val idColegio = crearColegio("IES Ramon y Cajal", "alta@ies.example")

        // El alumno se crea y la respuesta NO expone la contrasena ni su hash.
        val idAlumno = json.readTree(
            mockMvc.post("/api/v1/alumnos") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "nombre": "Ana",
                      "apellido": "Martinez",
                      "email": "ana@ies.example",
                      "password": "contrasena-larga",
                      "idColegio": $idColegio,
                      "jicpInicial": 10000.00
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("ana@ies.example") }
                jsonPath("$.jicpInicial") { value(10000.00) }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.passwordHash") { doesNotExist() }
            }.andReturn().response.contentAsString,
        ).get("id").asInt()

        val idProyecto = json.readTree(
            mockMvc.post("/api/v1/proyectos") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("ana@ies.example")}")
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

        mockMvc.get("/api/v1/proyectos/$idProyecto").andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("Eco-Drone Delivery") }
            jsonPath("$.creador.apellido") { value("Martinez") }
        }
    }

    @Test
    fun `el listado de proyectos admite filtros opcionales`() {
        val idColegio = crearColegio("IES Filtros", "filtros@ies.example")
        val idAlumno = crearAlumno(idColegio, "filtros.alumno@ies.example")

        mockMvc.post("/api/v1/proyectos") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenDe("filtros.alumno@ies.example")}")
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
        mockMvc.get("/api/v1/proyectos").andExpect { status { isOk() } }

        // Filtrando por colegio solo aparece el proyecto de ese colegio, con su creador.
        mockMvc.get("/api/v1/proyectos?idColegio=$idColegio").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].nombre") { value("Proyecto filtrable") }
            jsonPath("$.content[0].creador.idAlumno") { value(idAlumno) }
        }

        // Filtro combinado que no case con nada devuelve una pagina vacia.
        mockMvc.get("/api/v1/proyectos?idColegio=$idColegio&idCategoria=999999").andExpect {
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
              "password": "contrasena-larga",
              "idColegio": $idColegio
            }
        """.trimIndent()

        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `un alumno de un colegio inexistente devuelve 404`() {
        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Sin",
                  "apellido": "Colegio",
                  "email": "sincolegio@ies.example",
                  "password": "contrasena-larga",
                  "idColegio": 999999
                }
            """.trimIndent()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `los datos invalidos devuelven 422 con el detalle por campo`() {
        mockMvc.post("/api/v1/alumnos") {
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

    private fun crearColegio(nombre: String, email: String): Int {
        val respuesta = mockMvc.post("/api/v1/colegios") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"nombre": "$nombre", "direccion": "Calle Mayor 1", "email": "$email"}
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.nombre") { value(nombre) }
        }.andReturn().response.contentAsString

        val id = json.readTree(respuesta).get("id").asInt()
        assertEquals(true, id > 0, "el colegio debe recibir un id generado")
        return id
    }

    private val tokens = mutableMapOf<String, String>()

    private fun tokenDe(email: String): String = tokens.getOrPut(email) {
        val respuesta = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "password": "contrasena-larga"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        json.readTree(respuesta).get("accessToken").asText()
    }

    private fun crearAlumno(idColegio: Int, email: String): Int {
        val respuesta = mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nombre": "Alumno",
                  "apellido": "De Prueba",
                  "email": "$email",
                  "password": "contrasena-larga",
                  "idColegio": $idColegio
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return json.readTree(respuesta).get("id").asInt()
    }
}
