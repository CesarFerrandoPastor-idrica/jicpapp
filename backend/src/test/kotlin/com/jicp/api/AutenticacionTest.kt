package com.jicp.api

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertNotEquals

class AutenticacionTest : PruebaDeIntegracion() {

    @Test
    fun `el login devuelve tokens y yo describe al alumno del token`() {
        val idAlumno = darDeAltaAlumno("login.ok@ies.example", "IES Login")

        val tokens = login("login.ok@ies.example", CONTRASENA)

        mockMvc.get("/api/v1/yo") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.access}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.rol") { value("ALUMNO") }
            jsonPath("$.email") { value("login.ok@ies.example") }
            jsonPath("$.alumno.id") { value(idAlumno) }
            jsonPath("$.alumno.nombre") { value("Alumno") }
            jsonPath("$.alumno.jicpInicial") { value(SALDO_INICIAL.toDouble()) }
        }
    }

    @Test
    fun `el email se normaliza a minusculas para entrar`() {
        darDeAltaAlumno("mayusculas@ies.example", "IES Mayusculas")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "MaYuScUlAs@ies.example", "password": "$CONTRASENA"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `una contrasena incorrecta devuelve 401`() {
        darDeAltaAlumno("password.mala@ies.example", "IES Password")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "password.mala@ies.example", "password": "no-es-esta-contrasena"}"""
        }.andExpect {
            status { isUnauthorized() }
            // El mensaje no distingue entre email desconocido y contrasena mala.
            jsonPath("$.title") { value("Credenciales invalidas") }
        }
    }

    @Test
    fun `un email que no existe devuelve el mismo 401 que una contrasena mala`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "nadie@ies.example", "password": "$CONTRASENA"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("Credenciales invalidas") }
        }
    }

    @Test
    fun `yo sin token devuelve 401 en formato problem+json`() {
        mockMvc.get("/api/v1/yo").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("No autenticado") }
        }
    }

    @Test
    fun `un token manipulado no autentica`() {
        val tokens = login(darDeAltaAlumnoYDevolverEmail("manipulado@ies.example", "IES Manipulado"), CONTRASENA)

        // Se cambia el ultimo caracter de la firma: el contenido es el mismo, la firma ya no cuadra.
        val roto = tokens.access.dropLast(1) + if (tokens.access.last() == 'A') 'B' else 'A'

        mockMvc.get("/api/v1/yo") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $roto")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `el refresh rota el token y el anterior deja de servir`() {
        val primeros = login(darDeAltaAlumnoYDevolverEmail("rotacion@ies.example", "IES Rotacion"), CONTRASENA)

        val segundos = refrescar(primeros.refresh)
        assertNotEquals(primeros.refresh, segundos.refresh, "cada renovacion debe emitir un refresh nuevo")

        // El nuevo access token sirve.
        mockMvc.get("/api/v1/yo") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${segundos.access}")
        }.andExpect { status { isOk() } }

        // El refresh ya usado, no.
        postRefresh(primeros.refresh).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `reutilizar un refresh ya rotado corta la sesion entera`() {
        val primeros = login(darDeAltaAlumnoYDevolverEmail("reutilizado@ies.example", "IES Reutilizado"), CONTRASENA)
        val segundos = refrescar(primeros.refresh)

        // Alguien presenta el token viejo: o lo han robado, o el cliente guardo una copia.
        postRefresh(primeros.refresh).andExpect { status { isUnauthorized() } }

        // Como no se sabe quien es el legitimo, tambien muere el token que estaba vivo.
        postRefresh(segundos.refresh).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `el logout revoca el refresh token`() {
        val tokens = login(darDeAltaAlumnoYDevolverEmail("logout@ies.example", "IES Logout"), CONTRASENA)

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "${tokens.refresh}"}"""
        }.andExpect { status { isNoContent() } }

        postRefresh(tokens.refresh).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `sin token la API esta cerrada salvo lo publico`() {
        // Todo nace cerrado: tambien las lecturas que antes eran abiertas.
        for (ruta in listOf(
            "/api/v1/categorias",
            "/api/v1/proyectos",
            "/api/v1/proyectos/1/comentarios",
            "/api/v1/proyectos/1/mercado",
            "/api/v1/alumnos",
            "/api/v1/profesores",
            "/api/v1/colegios",
        )) {
            mockMvc.get(ruta).andExpect {
                status { isUnauthorized() }
                jsonPath("$.title") { value("No autenticado") }
            }
        }

        // Lo unico abierto a proposito.
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }
    }

    // --- utilidades ---

    private data class Tokens(val access: String, val refresh: String)

    private fun login(email: String, password: String): Tokens {
        val cuerpo = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "password": "$password"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
            jsonPath("$.rol") { value("ALUMNO") }
        }.andReturn().response.contentAsString

        val nodo = json.readTree(cuerpo)
        return Tokens(nodo.get("accessToken").asText(), nodo.get("refreshToken").asText())
    }

    private fun refrescar(refreshToken: String): Tokens {
        val cuerpo = postRefresh(refreshToken)
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        val nodo = json.readTree(cuerpo)
        return Tokens(nodo.get("accessToken").asText(), nodo.get("refreshToken").asText())
    }

    private fun postRefresh(refreshToken: String) = mockMvc.post("/api/v1/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"refreshToken": "$refreshToken"}"""
    }

    private fun darDeAltaAlumnoYDevolverEmail(email: String, nombreColegio: String): String {
        darDeAltaAlumno(email, nombreColegio)
        return email
    }

    private fun darDeAltaAlumno(email: String, nombreColegio: String): Int =
        crearAlumno(crearColegio(nombreColegio, "centro.$email"), email)
}
