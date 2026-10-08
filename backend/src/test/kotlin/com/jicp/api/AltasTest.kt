package com.jicp.api

import com.jicp.api.colegio.ColegioRepository
import com.jicp.api.profesor.Profesor
import com.jicp.api.profesor.ProfesorRepository
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Quien puede dar de alta y editar colegios y alumnos.
 *
 * Hoy, solo los desarrolladores con una cuenta ADMIN. Si algun dia se registran el propio
 * alumnado, el profesorado o una web externa, estos tests son los que habra que cambiar
 * a proposito, no los que se rompan sin querer.
 */
class AltasTest : PruebaDeIntegracion() {

    @Autowired private lateinit var profesores: ProfesorRepository
    @Autowired private lateinit var colegios: ColegioRepository

    @Test
    fun `sin token no se da de alta nada`() {
        mockMvc.post("/api/v1/colegios") {
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeColegio("IES Anonimo")
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/alumnos") {
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlumno("anonimo@ies.example", idColegio = 1)
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `un alumno no da de alta colegios ni alumnos`() {
        val idColegio = crearColegio("IES Alumno Sin Altas")
        crearAlumno(idColegio, "sin.altas@ies.example")
        val token = tokenDe("sin.altas@ies.example")

        mockMvc.post("/api/v1/colegios") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeColegio("IES Colado")
        }.andExpect { status { isForbidden() } }

        // Ni siquiera para darse de alta a si mismo un companero con mas dinero.
        mockMvc.post("/api/v1/alumnos") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlumno("companero.rico@ies.example", idColegio)
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un profesor tampoco da de alta alumnos`() {
        val idColegio = crearColegio("IES Profesor Sin Altas")
        profesores.save(
            Profesor(
                nombre = "Profesor",
                apellido = "Sin Altas",
                usuario = Usuario(
                    email = "profe.sinaltas@docentes.example",
                    passwordHash = passwordEncoder.encode(CONTRASENA),
                    rol = Rol.PROFESOR,
                ),
                colegio = colegios.findById(idColegio).orElseThrow(),
            ),
        )

        mockMvc.post("/api/v1/alumnos") {
            con(tokenDe("profe.sinaltas@docentes.example"))
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeAlumno("alumno.deprofe@ies.example", idColegio)
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un alumno no edita su ficha ni la de su colegio`() {
        val idColegio = crearColegio("IES Sin Ediciones")
        val idAlumno = crearAlumno(idColegio, "sin.ediciones@ies.example")
        val token = tokenDe("sin.ediciones@ies.example")

        mockMvc.put("/api/v1/alumnos/$idAlumno") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "Otro", "apellido": "Nombre"}"""
        }.andExpect { status { isForbidden() } }

        mockMvc.put("/api/v1/colegios/$idColegio") {
            con(token)
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeColegio("IES Renombrado")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un admin da de alta y edita colegios y alumnos`() {
        val idColegio = crearColegio("IES Del Admin")
        val idAlumno = crearAlumno(idColegio, "del.admin@ies.example")

        mockMvc.put("/api/v1/colegios/$idColegio") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = cuerpoDeColegio("IES Del Admin Renombrado")
        }.andExpect {
            status { isOk() }
            jsonPath("$.nombre") { value("IES Del Admin Renombrado") }
        }

        mockMvc.put("/api/v1/alumnos/$idAlumno") {
            con(tokenDeAdmin())
            contentType = MediaType.APPLICATION_JSON
            content = """{"nombre": "Nombre", "apellido": "Corregido"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.apellido") { value("Corregido") }
        }
    }

    private fun cuerpoDeColegio(nombre: String) =
        """{"nombre": "$nombre", "direccion": "Calle Mayor 1", "email": "info@ies.example"}"""

    private fun cuerpoDeAlumno(email: String, idColegio: Int) = """
        {
          "nombre": "Alumno", "apellido": "Nuevo",
          "email": "$email", "password": "$CONTRASENA",
          "idColegio": $idColegio, "jicpInicial": 99999999.00
        }
    """.trimIndent()
}
