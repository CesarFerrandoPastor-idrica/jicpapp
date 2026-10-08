package com.jicp.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/** Cambiar el propio email y la contrasena con `PATCH /yo`. */
class MiCuentaTest : PruebaDeIntegracion() {

    @Test
    fun `un profesor cambia su email y entra con el nuevo`() {
        val centro = crearColegio("IES Mi Cuenta")
        crearProfesor(centro, "viejo@cuenta.example")

        mockMvc.patch("/api/v1/yo") {
            con(tokenDe("viejo@cuenta.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "$CONTRASENA", "email": "Nuevo@Cuenta.example"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("nuevo@cuenta.example") }
            // El nombre no se toca.
            jsonPath("$.profesor.nombre") { value("Profesor") }
        }

        login("nuevo@cuenta.example", CONTRASENA).andExpect { status { isOk() } }
        login("viejo@cuenta.example", CONTRASENA).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `sin la contrasena actual correcta no se cambia nada`() {
        val centro = crearColegio("IES Cuenta Ajena")
        crearAlumno(centro, "propia@cuenta.example")

        mockMvc.patch("/api/v1/yo") {
            con(tokenDe("propia@cuenta.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "no-es-la-mia", "email": "robada@cuenta.example"}"""
        }.andExpect {
            // 403 y no 401: la sesion es buena, lo que falla es la comprobacion.
            status { isForbidden() }
        }

        login("propia@cuenta.example", CONTRASENA).andExpect { status { isOk() } }
    }

    @Test
    fun `no se puede usar el email de otro usuario`() {
        val centro = crearColegio("IES Cuenta Repetida")
        crearAlumno(centro, "uno@cuenta.example")
        crearAlumno(centro, "dos@cuenta.example")

        mockMvc.patch("/api/v1/yo") {
            con(tokenDe("uno@cuenta.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "$CONTRASENA", "email": "dos@cuenta.example"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `cambiar la contrasena cierra las sesiones abiertas`() {
        val centro = crearColegio("IES Cuenta Clave")
        crearAlumno(centro, "clave@cuenta.example")

        val refreshViejo = json.readTree(
            login("clave@cuenta.example", CONTRASENA).andReturn().response.contentAsString,
        ).get("refreshToken").asText()

        mockMvc.patch("/api/v1/yo") {
            con(tokenDe("clave@cuenta.example"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "$CONTRASENA", "passwordNueva": "otra-contrasena-larga"}"""
        }.andExpect { status { isOk() } }

        // La sesion que habia ya no se puede renovar...
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "$refreshViejo"}"""
        }.andExpect { status { isUnauthorized() } }

        // ...y la contrasena buena es la nueva.
        login("clave@cuenta.example", CONTRASENA).andExpect { status { isUnauthorized() } }
        login("clave@cuenta.example", "otra-contrasena-larga").andExpect { status { isOk() } }
    }

    @Test
    fun `sin nada que cambiar se avisa y los datos invalidos dan 422`() {
        val centro = crearColegio("IES Cuenta Vacia")
        crearAlumno(centro, "vacia@cuenta.example")
        val token = tokenDe("vacia@cuenta.example")

        mockMvc.patch("/api/v1/yo") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "$CONTRASENA", "email": "vacia@cuenta.example"}"""
        }.andExpect { status { isConflict() } }

        mockMvc.patch("/api/v1/yo") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"passwordActual": "", "email": "no-es-email", "passwordNueva": "corta"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errores.passwordActual") { exists() }
            jsonPath("$.errores.email") { exists() }
            jsonPath("$.errores.passwordNueva") { exists() }
        }

        mockMvc.get("/api/v1/yo") { con(token) }.andExpect {
            jsonPath("$.email") { value("vacia@cuenta.example") }
        }
    }

    private fun login(email: String, password: String) = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email", "password": "$password"}"""
    }
}
